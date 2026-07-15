package com.castigaro.thellmadventure.api

import android.content.Context
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.gen.ContentValidator
import com.castigaro.thellmadventure.world.Building
import com.castigaro.thellmadventure.world.BuildingStyle
import com.castigaro.thellmadventure.world.Frame
import com.castigaro.thellmadventure.world.GoalType
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Limits
import com.castigaro.thellmadventure.world.OutdoorMap
import com.castigaro.thellmadventure.world.Parcel
import com.castigaro.thellmadventure.world.SecretGoal
import com.castigaro.thellmadventure.world.Story
import com.castigaro.thellmadventure.world.Terrain
import com.castigaro.thellmadventure.world.WorldState
import org.json.JSONObject

/**
 * Die Story-Synthese beim Spielstart: EIN Aufruf verwandelt Basis-Story +
 * verdeckte Intake-Antworten in eine neue Geschichte — Titel, Prämisse,
 * geheimes Zielobjekt, Spieler-Aussehen, Startinventar und das Startgebiet
 * (die erste Außenparzelle samt mindestens einem Gebäude). Die Geschichte
 * ist danach für die ganze Session fixiert.
 *
 * Das Zielobjekt wird zunächst „versteckt" gehalten und erst von einer
 * späteren Generierung in der Welt platziert (Parcel-/RoomGenApi) — so
 * bleibt das Ziel maschinell prüfbar, ohne im Startgebiet zu liegen.
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
            appendLine(
                "- startgebiet: die erste Außenparzelle (gelaende = ${Limits.PARZELLE} Zeilen à " +
                    "${Limits.PARZELLE} Zeichen: g Gras, w Weg, s Sand, e Erde, b Blumen, ~ Wasser, f Fels; " +
                    "Zeile 1 = Nordkante). Setze 1-${Limits.MAX_GEBAEUDE} Gebäude (Grundriss 2-3 × 2-3, " +
                    "Tür an der Südseite) — ihr Inneres entsteht erst beim Betreten."
            )
            append("Kacheln (tx,ty) sind lokal und beginnen bei 0. Alles auf Deutsch.")
        }

        val turns = listOf(LlmClient.Turn(LlmClient.Turn.ROLE_USER, user))
        // 8192: Das Startgebiet (12 Geländezeilen, Gebäude, Requisiten) ist die
        // größte Einzelantwort des Spiels — 4096 wäre knapp.
        val raw = LlmClient.chat(context, system, turns, maxTokens = 8192, schema = Schemas.storySynthese())
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
            val raw2 = LlmClient.chat(context, system, retryTurns, maxTokens = 8192, schema = Schemas.storySynthese())
            build(LlmClient.extractJson(raw2), basis)
        }
        return save
    }

    private fun build(json: JSONObject, basis: Frame.BaseStory): GameSave {
        val gebietJson = json.optJSONObject("startgebiet")
            ?: throw ContentValidator.ValidationException("Feld 'startgebiet' fehlt")
        val zielJson = json.optJSONObject("zielItem")
            ?: throw ContentValidator.ValidationException("Feld 'zielItem' fehlt")

        val world = WorldState()
        world.outdoor = OutdoorMap()

        val zielItem = ContentValidator.buildItem(zielJson, world)
            ?: throw ContentValidator.ValidationException("zielItem braucht einen Namen")
        zielItem.holder = HOLDER_HIDDEN
        world.addItem(zielItem)

        // buildParcel trägt die Parzelle samt Gebäuden/NPCs/Items selbst ein.
        val start = ContentValidator.buildParcel(gebietJson, world, gx = 0, gy = 0)
        // Mindestens ein Gebäude — der Raum-Flow muss erreichbar bleiben.
        if (start.buildingIds.isEmpty()) {
            createFallbackHut(world, start)
        }

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
        world.addFact("Geschichte „$titel“ beginnt in ${start.id} ${start.name}")

        // Startposition: eine freie, begehbare Kachel nahe der Parzellenmitte
        val startTile = findStartTile(world, start)

        return GameSave(
            name = titel,
            world = world,
            roomId = GameSave.ORT_DRAUSSEN,
            ptx = startTile.first,
            pty = startTile.second,
            playerLook = ContentValidator.buildLook(json.optJSONObject("spielerAussehen")),
        )
    }

    /** Notfall-Hütte, falls die KI keine Gebäude gesetzt hat (zentral, mit Rand). */
    private fun createFallbackHut(world: WorldState, parcel: Parcel) {
        val outdoor = world.outdoor ?: return
        val g = Limits.PARZELLE
        val hut = Building(
            id = world.nextId("b"),
            name = "Windschiefe Hütte",
            tx = parcel.originTx() + g / 2 - 1,
            ty = parcel.originTy() + g / 2 - 2,
            w = 2,
            h = 2,
            stil = BuildingStyle.HUETTE,
        )
        // Grundriss und Eingang freiräumen (Requisiten weichen, Gelände wird Gras)
        parcel.props.removeAll { p -> hut.covers(p.tx, p.ty) || hut.doorTile() == p.tx to p.ty }
        for (y in hut.ty until hut.ty + hut.h) {
            for (x in hut.tx until hut.tx + hut.w) {
                parcel.setTerrainLocal(x - parcel.originTx(), y - parcel.originTy(), Terrain.GRAS)
            }
        }
        val dt = hut.doorTile()
        parcel.setTerrainLocal(dt.first - parcel.originTx(), dt.second - parcel.originTy(), Terrain.GRAS)
        outdoor.buildings[hut.id] = hut
        parcel.buildingIds.add(hut.id)
    }

    /** Begehbare Kachel nahe der Parzellenmitte (nicht auf einer Gebäudetür). */
    private fun findStartTile(world: WorldState, parcel: Parcel): Pair<Int, Int> {
        val outdoor = world.outdoor
        val g = Limits.PARZELLE
        val cx = parcel.originTx() + g / 2
        val cy = parcel.originTy() + g / 2
        fun geeignet(tx: Int, ty: Int): Boolean =
            outdoor != null && outdoor.walkable(tx, ty) &&
                outdoor.buildingDoorAt(tx, ty) == null
        if (geeignet(cx, cy)) return cx to cy
        for (r in 1..g) {
            for (ty in (cy - r)..(cy + r)) {
                for (tx in (cx - r)..(cx + r)) {
                    if (geeignet(tx, ty)) return tx to ty
                }
            }
        }
        return cx to cy
    }

    /** Holder-Wert des noch nicht platzierten Zielobjekts. */
    const val HOLDER_HIDDEN = "versteckt"
}
