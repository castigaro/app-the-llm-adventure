package com.castigaro.thellmadventure.api

import android.content.Context
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.game.StateDigest
import com.castigaro.thellmadventure.gen.ContentValidator
import com.castigaro.thellmadventure.world.Door
import com.castigaro.thellmadventure.world.Frame
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Limits
import com.castigaro.thellmadventure.world.Prop
import com.castigaro.thellmadventure.world.PropType
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.WorldState
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
        val world = save.world
        val goalItem = world.story?.geheimesZiel?.zielId?.let { world.items[it] }
        val goalUnplaced = goalItem?.holder == StorySynthApi.HOLDER_HIDDEN
        val forcePlace = goalUnplaced && world.rooms.size >= Limits.MAX_RAEUME - 2

        val system = buildString {
            appendLine(
                "Du bist der Spielleiter eines deutschsprachigen, prozeduralen Adventures. " +
                    "Der Spieler öffnet eine Tür in unentdecktes Gebiet; du erfindest den Raum dahinter — " +
                    "passend zur Story, im Ton der Welt, mit Verweisen auf Bestehendes wo sinnvoll. " +
                    "Antworte NUR mit einem JSON-Objekt nach dem vereinbarten Schema."
            )
            appendLine(Frame.katalogText())
            appendLine()
            appendLine(StateDigest.build(save))
        }
        val user = buildString {
            appendLine(
                "Der Spieler durchschreitet die Tür „${door.label}“ (Wand ${door.wall.name}) " +
                    "von ${originRoom.id} ${originRoom.name}."
            )
            appendLine("Erfinde den Raum dahinter (raum). Mindestens eine weitere Tür darf ins Ungewisse führen.")
            appendLine("fakt: EIN knapper Satz, was dieser Raum zur Geschichte beiträgt.")
            if (goalUnplaced) {
                appendLine(
                    "Das geheime Zielobjekt ${goalItem!!.id} (${goalItem.name}) ist noch nicht versteckt. " +
                        if (forcePlace) "Verstecke es JETZT hier: setze zielHier auf true."
                        else "Setze zielHier auf true, wenn es hier gut versteckt wäre (nicht zu früh)."
                )
            } else {
                appendLine("zielHier: immer false.")
            }
            append("musikNeu: optional eine kurze, sanfte Melodie für diesen Raum (oder null).")
        }

        val turns = listOf(LlmClient.Turn(LlmClient.Turn.ROLE_USER, user))
        val raw = LlmClient.chat(context, system, turns, maxTokens = 2048, schema = Schemas.raumGenerierung())
        return try {
            build(LlmClient.extractJson(raw), save, originRoom, door, goalItem, forcePlace)
        } catch (e: Exception) {
            if (e is LlmClient.ApiException && !e.message.orEmpty().startsWith("Antwort nicht lesbar")) throw e
            val retryTurns = turns +
                LlmClient.Turn(LlmClient.Turn.ROLE_ASSISTANT, raw) +
                LlmClient.Turn(
                    LlmClient.Turn.ROLE_USER,
                    "Deine Antwort war ungültig (${e.message}). Antworte erneut, exakt nach Schema, nur JSON.",
                )
            val raw2 = LlmClient.chat(context, system, retryTurns, maxTokens = 2048, schema = Schemas.raumGenerierung())
            build(LlmClient.extractJson(raw2), save, originRoom, door, goalItem, forcePlace)
        }
    }

    private fun build(
        json: JSONObject,
        save: GameSave,
        originRoom: Room,
        door: Door,
        goalItem: Item?,
        forcePlace: Boolean,
    ): Room {
        val world = save.world
        val raumJson = json.optJSONObject("raum")
            ?: throw ContentValidator.ValidationException("Feld 'raum' fehlt")

        val room = ContentValidator.buildRoom(raumJson, world, origin = originRoom to door)
        world.addRoom(room)
        door.targetRoomId = room.id

        // Zielobjekt verstecken, wenn gewünscht oder erzwungen
        if (goalItem != null && goalItem.holder == StorySynthApi.HOLDER_HIDDEN &&
            (json.optBoolean("zielHier", false) || forcePlace)
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
        world.addFact("${room.id} ${room.name} entdeckt hinter ${originRoom.id}.${door.wall.name}")
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

    private val CONTAINER_TYPES = setOf(
        PropType.KISTE, PropType.TRUHE, PropType.FASS, PropType.REGAL,
        PropType.KESSEL, PropType.ALTAR, PropType.BRUNNEN,
    )
}
