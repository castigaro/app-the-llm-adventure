package com.appsonar.thellmadventure.api

import android.content.Context
import com.appsonar.thellmadventure.data.GameSave
import com.appsonar.thellmadventure.game.StateDigest
import com.appsonar.thellmadventure.gen.ContentValidator
import com.appsonar.thellmadventure.world.Frame
import com.appsonar.thellmadventure.world.Item
import com.appsonar.thellmadventure.world.Limits
import com.appsonar.thellmadventure.world.Parcel
import com.appsonar.thellmadventure.world.Prop
import com.appsonar.thellmadventure.world.PropType
import com.appsonar.thellmadventure.world.WorldState
import org.json.JSONObject

/**
 * Parzellen-Generierung beim Betreten eines Nebelrands: EIN Aufruf erfindet
 * das 12×12-Gelände dahinter, passend zu Story und bisheriger Welt (Digest).
 * Die Nahtkanten aller existierenden Nachbarn werden der KI wörtlich
 * mitgegeben (Wege/Wasser fortsetzen); verlässlich macht die Naht aber erst
 * der Validator — Spielbarkeit hängt nie an der KI. Das Muster (ein Versuch,
 * ein Retry mit Fehler-Feedback) entspricht der RoomGenApi.
 */
object ParcelGenApi {

    /** Ergebnis: die neue Parzelle (bereits manifestiert und eingetragen). */
    fun generate(context: Context, save: GameSave, gx: Int, gy: Int): Parcel {
        val world = save.world
        val goalItem = world.story?.geheimesZiel?.zielId?.let { world.items[it] }
        val goalUnplaced = goalItem?.holder == StorySynthApi.HOLDER_HIDDEN
        val forcePlace = goalUnplaced && world.orteCount() >= Limits.MAX_ORTE - 2

        val system = buildString {
            appendLine(
                "Du bist der Spielleiter eines deutschsprachigen, prozeduralen Adventures. " +
                    "Der Spieler betritt unerforschtes Land am Rand der Außenkarte; du erfindest die " +
                    "12×12-Parzelle dahinter — passend zur Story, im Ton der Welt, mit Verweisen auf " +
                    "Bestehendes wo sinnvoll. Antworte NUR mit einem JSON-Objekt nach dem vereinbarten Schema."
            )
            appendLine(Frame.katalogText())
            appendLine()
            appendLine(StateDigest.build(save))
        }
        val user = buildString {
            appendLine("Der Spieler betritt unerforschtes Land: die Parzelle am Raster ($gx,$gy).")
            val naehte = seamLines(world, gx, gy)
            if (naehte.isNotEmpty()) {
                appendLine("NAHTKANTEN (fortsetzen — Wege/Wasser nicht abrupt enden lassen):")
                naehte.forEach { appendLine("  $it") }
            }
            appendLine(
                "gelaende: genau ${Limits.PARZELLE} Zeilen à ${Limits.PARZELLE} Zeichen " +
                    "(g Gras, w Weg, s Sand, e Erde, b Blumen, ~ Wasser, f Fels). " +
                    "Zeile 1 ist die Nordkante, Zeichen 1 die Westkante."
            )
            appendLine(
                "gebaeude: 0-${Limits.MAX_GEBAEUDE} Gebäude (Grundriss 2-3 × 2-3, tx/ty lokal 0..${Limits.PARZELLE - 1}, " +
                    "Tür an der Südseite). Das Innere entsteht erst beim Betreten."
            )
            appendLine("requisiten/figuren: tx/ty ebenfalls lokal 0..${Limits.PARZELLE - 1}, nur auf begehbarem Gelände.")
            appendLine("fakt: EIN knapper Satz, was dieses Gebiet zur Geschichte beiträgt.")
            if (goalUnplaced && world.orteCount() + 1 >= Limits.ZIEL_MIN_ORTE) {
                appendLine(
                    "Das geheime Zielobjekt ${goalItem!!.id} (${goalItem.name}) ist noch nicht versteckt. " +
                        if (forcePlace) "Verstecke es JETZT hier: setze zielHier auf true."
                        else "Setze zielHier auf true, wenn es hier gut versteckt wäre (nicht zu früh)."
                )
            } else {
                appendLine("zielHier: immer false.")
            }
            append("musikNeu: optional eine kurze, sanfte Melodie für dieses Gebiet (oder null).")
        }

        val turns = listOf(LlmClient.Turn(LlmClient.Turn.ROLE_USER, user))
        // 8192: 12 Geländezeilen + Gebäude + Requisiten (alle Felder Pflicht im
        // Schema) sprengen 2048 Tokens locker.
        val raw = LlmClient.chat(context, system, turns, maxTokens = 8192, schema = Schemas.parzellenGenerierung())
        return try {
            build(LlmClient.extractJson(raw), save, gx, gy, goalItem, forcePlace)
        } catch (e: Exception) {
            if (e is LlmClient.ApiException && !e.message.orEmpty().startsWith("Antwort nicht lesbar")) throw e
            val retryTurns = turns +
                LlmClient.Turn(LlmClient.Turn.ROLE_ASSISTANT, raw) +
                LlmClient.Turn(
                    LlmClient.Turn.ROLE_USER,
                    "Deine Antwort war ungültig (${e.message}). Antworte erneut, exakt nach Schema, nur JSON.",
                )
            val raw2 = LlmClient.chat(context, system, retryTurns, maxTokens = 8192, schema = Schemas.parzellenGenerierung())
            build(LlmClient.extractJson(raw2), save, gx, gy, goalItem, forcePlace)
        }
    }

    /** Die Kanten existierender Nachbarn als wörtliche Zeichenketten für den Prompt. */
    private fun seamLines(world: WorldState, gx: Int, gy: Int): List<String> {
        val outdoor = world.outdoor ?: return emptyList()
        val g = Limits.PARZELLE
        val zeilen = mutableListOf<String>()
        outdoor.parcelAt(gx - 1, gy)?.let { p ->
            val kante = (0 until g).map { p.terrainLocal(g - 1, it).zeichen }.joinToString("")
            zeilen.add("WEST-Kante deiner Parzelle grenzt an ${p.id} „${p.name}“ (deren Ostkante): \"$kante\"")
        }
        outdoor.parcelAt(gx + 1, gy)?.let { p ->
            val kante = (0 until g).map { p.terrainLocal(0, it).zeichen }.joinToString("")
            zeilen.add("OST-Kante deiner Parzelle grenzt an ${p.id} „${p.name}“ (deren Westkante): \"$kante\"")
        }
        outdoor.parcelAt(gx, gy - 1)?.let { p ->
            val kante = (0 until g).map { p.terrainLocal(it, g - 1).zeichen }.joinToString("")
            zeilen.add("NORD-Kante deiner Parzelle grenzt an ${p.id} „${p.name}“ (deren Südkante): \"$kante\"")
        }
        outdoor.parcelAt(gx, gy + 1)?.let { p ->
            val kante = (0 until g).map { p.terrainLocal(it, 0).zeichen }.joinToString("")
            zeilen.add("SÜD-Kante deiner Parzelle grenzt an ${p.id} „${p.name}“ (deren Nordkante): \"$kante\"")
        }
        return zeilen
    }

    private fun build(
        json: JSONObject,
        save: GameSave,
        gx: Int,
        gy: Int,
        goalItem: Item?,
        forcePlace: Boolean,
    ): Parcel {
        val world = save.world
        val parzellenJson = json.optJSONObject("parzelle")
            ?: throw ContentValidator.ValidationException("Feld 'parzelle' fehlt")

        // buildParcel trägt die Parzelle samt Gebäuden/NPCs/Items selbst ein.
        val parcel = ContentValidator.buildParcel(parzellenJson, world, gx, gy)

        // Zielobjekt verstecken, wenn gewünscht oder erzwungen — mit demselben
        // harten Guard gegen ein zu frühes Spielende wie bei Räumen.
        val zielHierErlaubt =
            json.optBoolean("zielHier", false) && world.orteCount() >= Limits.ZIEL_MIN_ORTE
        if (goalItem != null && goalItem.holder == StorySynthApi.HOLDER_HIDDEN &&
            (zielHierErlaubt || forcePlace)
        ) {
            placeGoalItem(world, parcel, goalItem)
        }

        json.optJSONObject("musikNeu")?.let { melJson ->
            ContentValidator.buildMelody(melJson, world)?.let { spec ->
                world.melodies[spec.id] = spec
                parcel.melodyId = spec.id
            }
        }

        json.optString("fakt").takeIf { it.isNotBlank() }?.let { world.addFact(it.take(140)) }
        world.addFact("${parcel.id} ${parcel.name} aufgedeckt am Raster ($gx,$gy)")
        return parcel
    }

    /** Versteckt das Zielobjekt in einer Behälter-Requisite der neuen Parzelle. */
    private fun placeGoalItem(world: WorldState, parcel: Parcel, goalItem: Item) {
        val container = parcel.props.firstOrNull { it.itemId == null && it.type in RoomGenApi.CONTAINER_TYPES }
            ?: parcel.props.firstOrNull { it.itemId == null }
            ?: createFallbackContainer(world, parcel)
        container.itemId = goalItem.id
        goalItem.holder = Item.holderProp(container.id)
        world.addFact("${goalItem.id} ruht nun in ${container.id} (${parcel.id})")
    }

    private fun createFallbackContainer(world: WorldState, parcel: Parcel): Prop {
        val outdoor = world.outdoor
        val g = Limits.PARZELLE
        var tile = parcel.originTx() + g / 2 to parcel.originTy() + g / 2
        outer@ for (ly in 1 until g - 1) {
            for (lx in 1 until g - 1) {
                val tx = parcel.originTx() + lx
                val ty = parcel.originTy() + ly
                val frei = parcel.terrainLocal(lx, ly).begehbar &&
                    parcel.props.none { it.tx == tx && it.ty == ty } &&
                    outdoor?.buildings?.values?.none { it.covers(tx, ty) || it.doorTile() == tx to ty } != false
                if (frei) {
                    tile = tx to ty
                    break@outer
                }
            }
        }
        val prop = Prop(
            id = world.nextId("p"),
            type = PropType.KISTE,
            tx = tile.first,
            ty = tile.second,
            name = "Unscheinbare Kiste",
            colorSlot = 6,
        )
        parcel.props.add(prop)
        return prop
    }
}
