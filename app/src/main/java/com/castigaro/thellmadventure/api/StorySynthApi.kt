package com.castigaro.thellmadventure.api

import android.content.Context
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.gen.ContentValidator
import com.castigaro.thellmadventure.world.Frame
import com.castigaro.thellmadventure.world.GoalType
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.SecretGoal
import com.castigaro.thellmadventure.world.Story
import com.castigaro.thellmadventure.world.WorldState
import org.json.JSONObject

/**
 * Die Story-Synthese beim Spielstart: EIN Aufruf verwandelt Basis-Story +
 * verdeckte Intake-Antworten in eine neue Geschichte — Titel, Prämisse,
 * geheimes Zielobjekt, Spieler-Aussehen, Startinventar und den ersten Raum.
 * Die Geschichte ist danach für die ganze Session fixiert.
 *
 * Das Zielobjekt wird zunächst „versteckt" gehalten und erst von einer
 * späteren Raum-Generierung in der Welt platziert (RoomGenApi) — so bleibt
 * das Ziel maschinell prüfbar, ohne im ersten Raum zu liegen.
 */
object StorySynthApi {

    fun synthesize(context: Context, basis: Frame.BaseStory, tags: List<String>): GameSave {
        val system = buildString {
            appendLine(
                "Du bist der Spielleiter eines deutschsprachigen, prozeduralen Adventures. " +
                    "Du erschaffst eine neue Geschichte aus einer Basis-Story und den Neigungen " +
                    "des Spielers. Antworte NUR mit einem JSON-Objekt nach dem vereinbarten Schema, " +
                    "ohne Erklärtext."
            )
            appendLine(Frame.katalogText())
        }
        val user = buildString {
            appendLine("BASIS-STORY: ${basis.titel} — ${basis.settingKarte}")
            appendLine("MOTIV-SUBSET (mische 1-2 davon ein): ${basis.motive.joinToString("; ")}")
            appendLine("NEIGUNGEN DES SPIELERS (aus verdeckten Fragen): ${tags.joinToString(", ")}")
            appendLine()
            appendLine("Erschaffe:")
            appendLine("- titel: klingender Titel der neuen Geschichte")
            appendLine("- praemisse: 2-4 Sätze roter Faden")
            appendLine("- ton: 1-3 Wörter Stimmung")
            appendLine("- zielItem: das geheime Zielobjekt (wird später in der Welt versteckt; Spieler gewinnt, wenn er es besitzt)")
            appendLine("- zielUmschreibung: 1 Satz, warum dieses Objekt das Ziel ist")
            appendLine("- spielerAussehen: Aussehen der Spielfigur aus dem Katalog")
            appendLine("- startInventar: 0-2 Gegenstände, die der Spieler zu Beginn trägt")
            appendLine("- startraum: der erste Raum. Mindestens 1 Tür bleibt unentdeckt (führt ins Ungewisse).")
            append("Requisiten-Kacheln (tx,ty) beginnen bei 0. Alles auf Deutsch.")
        }

        val turns = listOf(LlmClient.Turn(LlmClient.Turn.ROLE_USER, user))
        val raw = LlmClient.chat(context, system, turns, maxTokens = 4096, schema = Schemas.storySynthese())
        val save = try {
            build(LlmClient.extractJson(raw), basis)
        } catch (e: Exception) {
            if (e is LlmClient.ApiException && !e.message.orEmpty().startsWith("Antwort nicht lesbar")) throw e
            // Ein Wiederholungsversuch mit Fehler-Feedback.
            val retryTurns = turns +
                LlmClient.Turn(LlmClient.Turn.ROLE_ASSISTANT, raw) +
                LlmClient.Turn(
                    LlmClient.Turn.ROLE_USER,
                    "Deine Antwort war ungültig (${e.message}). Antworte erneut, exakt nach Schema, nur JSON.",
                )
            val raw2 = LlmClient.chat(context, system, retryTurns, maxTokens = 4096, schema = Schemas.storySynthese())
            build(LlmClient.extractJson(raw2), basis)
        }
        return save
    }

    private fun build(json: JSONObject, basis: Frame.BaseStory): GameSave {
        val startraumJson = json.optJSONObject("startraum")
            ?: throw ContentValidator.ValidationException("Feld 'startraum' fehlt")
        val zielJson = json.optJSONObject("zielItem")
            ?: throw ContentValidator.ValidationException("Feld 'zielItem' fehlt")

        val world = WorldState()

        val zielItem = ContentValidator.buildItem(zielJson, world)
            ?: throw ContentValidator.ValidationException("zielItem braucht einen Namen")
        zielItem.holder = HOLDER_HIDDEN
        world.addItem(zielItem)

        val startraum = ContentValidator.buildRoom(startraumJson, world, origin = null)
        // Mindestens eine unentdeckte Tür — sonst kann die Welt nicht wachsen.
        if (startraum.doors.none { it.targetRoomId == null }) {
            throw ContentValidator.ValidationException("startraum braucht mindestens eine unentdeckte Tür")
        }
        world.addRoom(startraum)

        // Startinventar (max 2)
        json.optJSONArray("startInventar")?.let { arr ->
            for (i in 0 until minOf(arr.length(), 2)) {
                arr.optJSONObject(i)?.let { itemJson ->
                    ContentValidator.buildItem(itemJson, world)?.let { item ->
                        item.holder = Item.HOLDER_INVENTORY
                        world.addItem(item)
                    }
                }
            }
        }

        val titel = json.optString("titel").ifBlank { basis.titel }.take(60)
        world.story = Story(
            titel = titel,
            praemisse = json.optString("praemisse").take(600),
            ton = json.optString("ton").take(40),
            geheimesZiel = SecretGoal(
                typ = GoalType.BESITZE_ITEM,
                zielId = zielItem.id,
                umschreibung = json.optString("zielUmschreibung").take(200),
            ),
            basisStory = basis.id,
        )
        world.addFact("Geschichte „$titel“ beginnt in ${startraum.id}")

        // Startposition: eine freie Kachel nahe der Raummitte
        val startTile = findStartTile(startraum)

        return GameSave(
            name = titel,
            world = world,
            roomId = startraum.id,
            ptx = startTile.first,
            pty = startTile.second,
            playerLook = ContentValidator.buildLook(json.optJSONObject("spielerAussehen")),
        )
    }

    private fun findStartTile(room: Room): Pair<Int, Int> {
        val cx = room.w / 2
        val cy = room.h / 2
        if (room.walkable(cx, cy) && room.doorAt(cx, cy) == null) return cx to cy
        for (r in 1..maxOf(room.w, room.h)) {
            for (ty in (cy - r)..(cy + r)) {
                for (tx in (cx - r)..(cx + r)) {
                    if (tx in 0 until room.w && ty in 0 until room.h &&
                        room.walkable(tx, ty) && room.doorAt(tx, ty) == null
                    ) {
                        return tx to ty
                    }
                }
            }
        }
        return cx to cy
    }

    /** Holder-Wert des noch nicht platzierten Zielobjekts. */
    const val HOLDER_HIDDEN = "versteckt"
}
