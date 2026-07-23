package com.appsonar.thellmadventure.api

import android.content.Context
import com.appsonar.thellmadventure.data.GameSave
import com.appsonar.thellmadventure.game.StateDigest
import com.appsonar.thellmadventure.gen.ContentValidator
import com.appsonar.thellmadventure.world.Building
import com.appsonar.thellmadventure.world.Door
import com.appsonar.thellmadventure.world.Frame
import com.appsonar.thellmadventure.world.Item
import com.appsonar.thellmadventure.world.Limits
import com.appsonar.thellmadventure.world.Prop
import com.appsonar.thellmadventure.world.PropType
import com.appsonar.thellmadventure.world.Room
import com.appsonar.thellmadventure.world.WorldState
import org.json.JSONObject

/**
 * Raum-Generierung beim Durchschreiten einer unentdeckten Tür: EIN Aufruf
 * erfindet den Raum dahinter, passend zu Story und bisheriger Welt (Digest).
 * Der Raum manifestiert sich dauerhaft; die Tür wird verdrahtet und die
 * Rücktür erzwungen. Das geheime Zielobjekt wird auf Wunsch der KI (oder
 * spätestens kurz vor dem Raumlimit) hier versteckt.
 */
object RoomGenApi {

    /** Ergebnis: der neue Raum (bereits manifestiert und verdrahtet). */
    fun generate(context: Context, save: GameSave, originRoom: Room, door: Door): Room {
        val user = buildString {
            appendLine(
                "Der Spieler durchschreitet die Tür „${door.label}“ (Wand ${door.wall.name}) " +
                    "von ${originRoom.id} ${originRoom.name}."
            )
            appendLine("Erfinde den Raum dahinter (raum). Mindestens eine weitere Tür darf ins Ungewisse führen.")
        }
        return request(context, save, user) { json, goalItem, forcePlace ->
            build(json, save, origin = originRoom to door, outside = null, goalItem, forcePlace).also {
                door.targetRoomId = it.id
                save.world.addFact("${it.id} ${it.name} entdeckt hinter ${originRoom.id}.${door.wall.name}")
            }
        }
    }

    /**
     * Innenraum eines Gebäudes der Außenkarte: Die Rücktür nach draußen wird
     * auf der Südwand erzwungen (Ziel `welt:<gebaeudeId>`); tiefere Türen
     * führen wie gewohnt in weitere, frisch generierte Räume.
     */
    fun generateInterior(context: Context, save: GameSave, building: Building): Room {
        val user = buildString {
            appendLine(
                "Der Spieler betritt das Gebäude „${building.name}“ " +
                    "(${building.stil.name.lowercase()}, ${building.w}×${building.h} Grundriss) von draußen."
            )
            appendLine(
                "Erfinde den Innenraum (raum), passend zum Äußeren. Die Tür nach draußen liegt an der " +
                    "Südwand und existiert bereits — weitere Türen dürfen ins Ungewisse führen."
            )
        }
        return request(context, save, user) { json, goalItem, forcePlace ->
            build(json, save, origin = null, outside = building, goalItem, forcePlace).also {
                building.targetRoomId = it.id
                save.world.addFact("${it.id} ${it.name} — das Innere von ${building.id} (${building.name})")
            }
        }
    }

    /** Gemeinsamer Ablauf: Prompt bauen, ein Versuch, ein Retry mit Feedback. */
    private fun request(
        context: Context,
        save: GameSave,
        userIntro: String,
        buildFrom: (JSONObject, Item?, Boolean) -> Room,
    ): Room {
        val world = save.world
        val goalItem = world.story?.geheimesZiel?.zielId?.let { world.items[it] }
        val goalUnplaced = goalItem?.holder == StorySynthApi.HOLDER_HIDDEN
        val forcePlace = goalUnplaced && world.orteCount() >= Limits.MAX_ORTE - 2

        val system = buildString {
            appendLine(
                "Du bist der Spielleiter eines deutschsprachigen, prozeduralen Adventures. " +
                    "Der Spieler betritt unentdecktes Gebiet; du erfindest den Raum dahinter — " +
                    "passend zur Story, im Ton der Welt, mit Verweisen auf Bestehendes wo sinnvoll. " +
                    "Antworte NUR mit einem JSON-Objekt nach dem vereinbarten Schema."
            )
            appendLine(Frame.katalogText())
            appendLine()
            appendLine(StateDigest.build(save))
        }
        val user = buildString {
            append(userIntro)
            appendLine("fakt: EIN knapper Satz, was dieser Raum zur Geschichte beiträgt.")
            if (goalUnplaced && world.orteCount() + 1 >= Limits.ZIEL_MIN_ORTE) {
                appendLine(
                    "Das geheime Zielobjekt ${goalItem!!.id} (${goalItem.name}) ist noch nicht versteckt. " +
                        if (forcePlace) "Verstecke es JETZT hier: setze zielHier auf true."
                        else "Setze zielHier auf true, wenn es hier gut versteckt wäre (nicht zu früh)."
                )
            } else {
                // Auch solange die Welt zu klein ist: Das Ziel bleibt verborgen,
                // damit das Abenteuer nicht nach zwei Orten endet.
                appendLine("zielHier: immer false.")
            }
            append("musikNeu: optional eine kurze, sanfte Melodie für diesen Raum (oder null).")
        }

        val turns = listOf(LlmClient.Turn(LlmClient.Turn.ROLE_USER, user))
        val raw = LlmClient.chat(context, system, turns, maxTokens = 4096, schema = Schemas.raumGenerierung())
        return try {
            buildFrom(LlmClient.extractJson(raw), goalItem, forcePlace)
        } catch (e: Exception) {
            if (e is LlmClient.ApiException && !e.message.orEmpty().startsWith("Antwort nicht lesbar")) throw e
            val retryTurns = turns +
                LlmClient.Turn(LlmClient.Turn.ROLE_ASSISTANT, raw) +
                LlmClient.Turn(
                    LlmClient.Turn.ROLE_USER,
                    "Deine Antwort war ungültig (${e.message}). Antworte erneut, exakt nach Schema, nur JSON.",
                )
            val raw2 = LlmClient.chat(context, system, retryTurns, maxTokens = 4096, schema = Schemas.raumGenerierung())
            buildFrom(LlmClient.extractJson(raw2), goalItem, forcePlace)
        }
    }

    private fun build(
        json: JSONObject,
        save: GameSave,
        origin: Pair<Room, Door>?,
        outside: Building?,
        goalItem: Item?,
        forcePlace: Boolean,
    ): Room {
        val world = save.world
        val raumJson = json.optJSONObject("raum")
            ?: throw ContentValidator.ValidationException("Feld 'raum' fehlt")

        val room = ContentValidator.buildRoom(
            raumJson, world,
            origin = origin,
            outsideId = outside?.let { "$WELT_PREFIX${it.id}" },
        )
        world.addRoom(room)

        // Zielobjekt verstecken, wenn gewünscht oder erzwungen. Harter Guard
        // gegen ein zu frühes Spielende: Vor ZIEL_MIN_ORTE Orten wird ein
        // zielHier der KI ignoriert (die Prompt-Bitte allein reicht nicht).
        val zielHierErlaubt =
            json.optBoolean("zielHier", false) && world.orteCount() >= Limits.ZIEL_MIN_ORTE
        if (goalItem != null && goalItem.holder == StorySynthApi.HOLDER_HIDDEN &&
            (zielHierErlaubt || forcePlace)
        ) {
            placeGoalItem(world, room, goalItem)
        }

        // Melodie (Wiedergabe ab Phase 5; Daten manifestieren jetzt schon)
        json.optJSONObject("musikNeu")?.let { melJson ->
            ContentValidator.buildMelody(melJson, world)?.let { spec ->
                world.melodies[spec.id] = spec
                room.melodyId = spec.id
            }
        }

        json.optString("fakt").takeIf { it.isNotBlank() }?.let { world.addFact(it.take(140)) }
        return room
    }

    /** Versteckt das Zielobjekt in einer Behälter-Requisite des neuen Raums. */
    private fun placeGoalItem(world: WorldState, room: Room, goalItem: Item) {
        val container = room.props.firstOrNull { it.itemId == null && it.type in CONTAINER_TYPES }
            ?: room.props.firstOrNull { it.itemId == null }
            ?: createFallbackContainer(world, room)
        container.itemId = goalItem.id
        goalItem.holder = Item.holderProp(container.id)
        world.addFact("${goalItem.id} ruht nun in ${container.id} (${room.id})")
    }

    private fun createFallbackContainer(world: WorldState, room: Room): Prop {
        val doorTiles = room.doors.map { it.tile(room.w, room.h) }.toSet()
        var tile = room.w / 2 to room.h / 2
        outer@ for (ty in 0 until room.h) {
            for (tx in 0 until room.w) {
                if (tx to ty !in doorTiles && room.propAt(tx, ty) == null) {
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
        room.props.add(prop)
        return prop
    }

    /** Präfix eines Tür-Ziels, das nach draußen führt: `welt:<gebaeudeId>`. */
    const val WELT_PREFIX = "welt:"

    internal val CONTAINER_TYPES = setOf(
        PropType.KISTE, PropType.TRUHE, PropType.FASS, PropType.REGAL,
        PropType.KESSEL, PropType.ALTAR, PropType.BRUNNEN,
    )
}
