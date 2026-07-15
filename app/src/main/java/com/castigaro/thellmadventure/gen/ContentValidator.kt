package com.castigaro.thellmadventure.gen

import com.castigaro.thellmadventure.data.enumOrNull
import com.castigaro.thellmadventure.game.Pathfinder
import com.castigaro.thellmadventure.world.BodyShape
import com.castigaro.thellmadventure.world.Door
import com.castigaro.thellmadventure.world.Headgear
import com.castigaro.thellmadventure.world.IconArchetype
import com.castigaro.thellmadventure.world.IconMaterial
import com.castigaro.thellmadventure.world.IconSpec
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Limits
import com.castigaro.thellmadventure.world.Look
import com.castigaro.thellmadventure.world.MelodySpec
import com.castigaro.thellmadventure.world.Note
import com.castigaro.thellmadventure.world.Npc
import com.castigaro.thellmadventure.world.PROP_STATES
import com.castigaro.thellmadventure.world.Palette
import com.castigaro.thellmadventure.world.Prop
import com.castigaro.thellmadventure.world.PropType
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.Chord
import com.castigaro.thellmadventure.world.SLOT_COLORS
import com.castigaro.thellmadventure.world.Timbre
import com.castigaro.thellmadventure.world.Wall
import com.castigaro.thellmadventure.world.WorldState
import org.json.JSONObject

/**
 * Der Torwächter zwischen KI-Antwort und Weltzustand: prüft gegen das feste
 * Vokabular, klemmt alle Größen in den Rahmen, erzwingt die Rücktür zum
 * Herkunftsraum und stellt Begehbarkeit sicher. Unbekanntes wird verworfen,
 * Ungültiges auf Defaults gesetzt — die KI kann erschaffen, aber nichts
 * kaputt machen.
 *
 * Harte Fehler (fehlende Pflichtteile) werfen [ValidationException] BEVOR
 * der Weltzustand angefasst wird — der Aufrufer nutzt die Meldung für den
 * einen Wiederholungsversuch mit Fehler-Feedback.
 */
object ContentValidator {

    class ValidationException(message: String) : Exception(message)

    // ---- Raum ----------------------------------------------------------------

    /**
     * Baut aus einer KI-Raumantwort einen manifestierten Raum inklusive
     * enthaltener Gegenstände und Figuren und trägt alles in [world] ein.
     * [origin] (Herkunftsraum + durchschrittene Tür) erzwingt die Rücktür.
     */
    fun buildRoom(json: JSONObject, world: WorldState, origin: Pair<Room, Door>? = null): Room {
        val w = json.optInt("breite", Limits.MIN_RAUM_W).coerceIn(Limits.MIN_RAUM_W, Limits.MAX_RAUM_W)
        val h = json.optInt("hoehe", Limits.MIN_RAUM_H).coerceIn(Limits.MIN_RAUM_H, Limits.MAX_RAUM_H)
        val room = Room(
            id = world.nextId("r"),
            name = json.optString("name").ifBlank { "Namenloser Raum" }.take(40),
            w = w,
            h = h,
            palette = enumOrNull<Palette>(json.optString("palette")) ?: Palette.STAHL,
        )

        // Türen: klemmen, deduplizieren, kappen
        json.optJSONArray("tueren")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                if (room.doors.size >= Limits.MAX_TUEREN) break
                val wall = enumOrNull<Wall>(t.optString("wand")) ?: continue
                val maxPos = if (wall == Wall.N || wall == Wall.S) w - 1 else h - 1
                val pos = t.optInt("pos", maxPos / 2).coerceIn(0, maxPos)
                if (room.doors.any { it.wall == wall && it.pos == pos }) continue
                room.doors.add(
                    Door(
                        wall = wall,
                        pos = pos,
                        targetRoomId = null,
                        locked = t.optBoolean("verschlossen", false),
                        lockHint = t.optString("hinweis").takeIf { it.isNotBlank() && it != "null" }?.take(120),
                        label = t.optString("name").ifBlank { "Tür" }.take(24),
                    )
                )
            }
        }

        // Rücktür zum Herkunftsraum erzwingen (nie verschlossen — keine Falle).
        if (origin != null) {
            val backWall = opposite(origin.second.wall)
            val existing = room.doors.firstOrNull { it.wall == backWall }
            if (existing != null) {
                existing.targetRoomId = origin.first.id
                existing.locked = false
                existing.label = origin.first.name.take(24)
            } else {
                if (room.doors.size >= Limits.MAX_TUEREN) {
                    room.doors.removeAt(room.doors.size - 1)
                }
                val maxPos = if (backWall == Wall.N || backWall == Wall.S) w - 1 else h - 1
                room.doors.add(
                    Door(backWall, maxPos / 2, targetRoomId = origin.first.id, label = origin.first.name.take(24))
                )
            }
        }
        if (room.doors.isEmpty()) {
            room.doors.add(Door(Wall.N, w / 2, targetRoomId = null, label = "Tür"))
        }

        val doorTiles = room.doors.map { it.tile(room.w, room.h) }.toSet()

        // Requisiten: Vokabular-Filter, Kachel-Klemmen, Kollisionen verwerfen
        json.optJSONArray("requisiten")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                if (room.props.size >= Limits.MAX_PROPS) break
                val type = enumOrNull<PropType>(p.optString("typ")) ?: continue
                val tx = p.optInt("tx", 1).coerceIn(0, w - 1)
                val ty = p.optInt("ty", 1).coerceIn(0, h - 1)
                if (tx to ty in doorTiles) continue
                if (room.props.any { it.tx == tx && it.ty == ty }) continue
                val prop = Prop(
                    id = world.nextId("p"),
                    type = type,
                    tx = tx,
                    ty = ty,
                    name = p.optString("name").ifBlank { type.name.lowercase() }.take(40),
                    colorSlot = p.optInt("farbe", 0).coerceIn(0, SLOT_COLORS.size - 1),
                    blocksTile = p.optBoolean("blockiert", true),
                )
                p.optJSONObject("zustaende")?.let { z ->
                    z.keys().forEach { key ->
                        if (key in PROP_STATES && z.optBoolean(key, false)) prop.states[key] = true
                    }
                }
                p.optJSONObject("enthaeltItem")?.let { itemJson ->
                    val item = buildItem(itemJson, world) ?: return@let
                    item.holder = Item.holderProp(prop.id)
                    prop.itemId = item.id
                    world.addItem(item)
                }
                room.props.add(prop)
            }
        }

        // Figuren
        json.optJSONArray("figuren")?.let { arr ->
            for (i in 0 until arr.length()) {
                if (room.npcIds.size >= Limits.MAX_NPCS) break
                val f = arr.optJSONObject(i) ?: continue
                val name = f.optString("name").ifBlank { "Fremde" }.take(30)
                var tx = f.optInt("tx", w / 2).coerceIn(0, w - 1)
                var ty = f.optInt("ty", h / 2).coerceIn(0, h - 1)
                // Nicht auf Türen oder blockierenden Requisiten stehen
                if (tx to ty in doorTiles || room.props.any { it.blocksTile && it.tx == tx && it.ty == ty }) {
                    val frei = freeTile(room, doorTiles) ?: continue
                    tx = frei.first
                    ty = frei.second
                }
                val npc = Npc(
                    id = world.nextId("n"),
                    name = name,
                    roomId = room.id,
                    tx = tx,
                    ty = ty,
                    look = buildLook(f.optJSONObject("aussehen")),
                    charakterkarte = f.optString("charakterkarte").take(400),
                    rolle = f.optString("rolle").ifBlank { "Bewohner" }.take(40),
                )
                world.addNpc(npc)
                room.npcIds.add(npc.id)
            }
        }

        ensureConnectivity(room)
        return room
    }

    /**
     * Alle Türkacheln müssen sich gegenseitig erreichen können — der Spieler
     * darf sich nie einmauern. Notfalls werden blockierende Requisiten
     * (von hinten beginnend) begehbar gemacht.
     */
    private fun ensureConnectivity(room: Room) {
        val doorTiles = room.doors.map { it.tile(room.w, room.h) }
        val start = doorTiles.firstOrNull() ?: return
        while (true) {
            val allReachable = doorTiles.drop(1).all { target ->
                target == start ||
                    Pathfinder.findPath(room.w, room.h, room::walkable, start, target).isNotEmpty()
            }
            if (allReachable) return
            val blocker = room.props.lastOrNull { it.blocksTile } ?: return
            blocker.blocksTile = false
        }
    }

    private fun freeTile(room: Room, doorTiles: Set<Pair<Int, Int>>): Pair<Int, Int>? {
        for (ty in 1 until room.h - 1) {
            for (tx in 1 until room.w - 1) {
                if (tx to ty !in doorTiles && room.walkable(tx, ty)) return tx to ty
            }
        }
        return null
    }

    private fun opposite(wall: Wall): Wall = when (wall) {
        Wall.N -> Wall.S
        Wall.S -> Wall.N
        Wall.W -> Wall.O
        Wall.O -> Wall.W
    }

    // ---- Gegenstände, Aussehen, Melodien --------------------------------------

    /** Baut einen Gegenstand (ohne Holder-Zuweisung). Null bei fehlendem Namen. */
    fun buildItem(json: JSONObject, world: WorldState): Item? {
        val name = json.optString("name").takeIf { it.isNotBlank() } ?: return null
        return Item(
            id = world.nextId("i"),
            name = name.take(40),
            beschreibung = json.optString("beschreibung").take(160),
            icon = buildIcon(json.optJSONObject("icon"), name),
        )
    }

    fun buildIcon(json: JSONObject?, fallbackName: String): IconSpec {
        if (json == null) return IconSpec.derived(fallbackName)
        val motiv = enumOrNull<IconArchetype>(json.optString("motiv"))
            ?: return IconSpec.derived(fallbackName)
        return IconSpec(
            colorIdx = json.optInt("farbe", 0).coerceIn(0, SLOT_COLORS.size - 1),
            motiv = motiv,
            material = enumOrNull<IconMaterial>(json.optString("material")) ?: IconMaterial.EISEN,
            leuchtet = json.optBoolean("leuchtet", false),
            abgenutzt = json.optBoolean("abgenutzt", false),
        )
    }

    fun buildLook(json: JSONObject?): Look {
        if (json == null) return Look()
        return Look(
            body = enumOrNull<BodyShape>(json.optString("koerper")) ?: BodyShape.TRAPEZ,
            colorA = json.optInt("farbeA", 0).coerceIn(0, SLOT_COLORS.size - 1),
            colorB = json.optInt("farbeB", 3).coerceIn(0, SLOT_COLORS.size - 1),
            headgear = enumOrNull<Headgear>(json.optString("kopf")) ?: Headgear.KEINE,
        )
    }

    /** Klemmt eine KI-Melodie in den Rahmen; null bei unbrauchbarer Struktur. */
    fun buildMelody(json: JSONObject?, world: WorldState): MelodySpec? {
        if (json == null) return null
        val chords = mutableListOf<Chord>()
        json.optJSONArray("akkorde")?.let { arr ->
            for (i in 0 until arr.length()) {
                enumOrNull<Chord>(arr.optString(i))?.let(chords::add)
            }
        }
        if (chords.isEmpty()) return null
        val bass = mutableListOf<Int>()
        json.optJSONArray("bass")?.let { arr ->
            for (i in 0 until arr.length()) bass.add(arr.optInt(i, 48).coerceIn(36, 72))
        }
        val melody = mutableListOf<Note>()
        json.optJSONArray("melodie")?.let { arr ->
            for (i in 0 until arr.length()) {
                if (melody.size >= 64) break
                val note = Note.fromJson(arr.optJSONArray(i)) ?: continue
                val midi = if (note.midi == 0) 0 else note.midi.coerceIn(48, 84)
                melody.add(Note(midi, note.dauer))
            }
        }
        return MelodySpec(
            id = world.nextId("m"),
            tempoBpm = json.optInt("tempo", 84).coerceIn(60, 120),
            timbre = enumOrNull<Timbre>(json.optString("klang")) ?: Timbre.WEICH,
            chords = chords.take(8),
            bass = bass.take(8),
            melody = melody,
        )
    }
}
