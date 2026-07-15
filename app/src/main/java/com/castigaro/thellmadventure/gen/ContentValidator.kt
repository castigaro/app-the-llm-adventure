package com.castigaro.thellmadventure.gen

import com.castigaro.thellmadventure.data.enumOrNull
import com.castigaro.thellmadventure.game.Pathfinder
import com.castigaro.thellmadventure.world.BodyShape
import com.castigaro.thellmadventure.world.Building
import com.castigaro.thellmadventure.world.BuildingStyle
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
import com.castigaro.thellmadventure.world.OutdoorMap
import com.castigaro.thellmadventure.world.PROP_STATES
import com.castigaro.thellmadventure.world.Palette
import com.castigaro.thellmadventure.world.Parcel
import com.castigaro.thellmadventure.world.Prop
import com.castigaro.thellmadventure.world.PropType
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.Chord
import com.castigaro.thellmadventure.world.SLOT_COLORS
import com.castigaro.thellmadventure.world.Terrain
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
     * [outsideId]/[outsideLabel] erzwingen stattdessen eine Rücktür nach
     * draußen (Südwand) — für Gebäude-Innenräume der Außenkarte.
     */
    fun buildRoom(
        json: JSONObject,
        world: WorldState,
        origin: Pair<Room, Door>? = null,
        outsideId: String? = null,
        outsideLabel: String = "Nach draußen",
    ): Room {
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

        // Rücktür erzwingen (nie verschlossen — keine Falle): zum Herkunfts-
        // raum auf der Gegenwand, oder nach draußen auf der Südwand.
        val back: Triple<Wall, String, String>? = when {
            origin != null -> Triple(opposite(origin.second.wall), origin.first.id, origin.first.name.take(24))
            outsideId != null -> Triple(Wall.S, outsideId, outsideLabel.take(24))
            else -> null
        }
        if (back != null) {
            val (backWall, backTarget, backLabel) = back
            val existing = room.doors.firstOrNull { it.wall == backWall }
            if (existing != null) {
                existing.targetRoomId = backTarget
                existing.locked = false
                existing.label = backLabel
            } else {
                if (room.doors.size >= Limits.MAX_TUEREN) {
                    room.doors.removeAt(room.doors.size - 1)
                }
                val maxPos = if (backWall == Wall.N || backWall == Wall.S) w - 1 else h - 1
                room.doors.add(
                    Door(backWall, maxPos / 2, targetRoomId = backTarget, label = backLabel)
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

    // ---- Außenparzelle ---------------------------------------------------------

    /**
     * Baut aus einer KI-Parzellenantwort eine manifestierte Parzelle am
     * Raster (gx,gy) und trägt sie samt Gebäuden/NPCs/Items in world/outdoor
     * ein: Gelände klemmen, Nähte zu Nachbarn deterministisch reparieren
     * (Spielbarkeit hängt nie an der KI) und Zusammenhang sicherstellen.
     */
    fun buildParcel(json: JSONObject, world: WorldState, gx: Int, gy: Int): Parcel {
        val outdoor = world.outdoor
            ?: throw ValidationException("Keine Außenkarte im Weltzustand")
        if (outdoor.parcelAt(gx, gy) != null) {
            throw ValidationException("Parzelle ($gx,$gy) existiert bereits")
        }
        val g = Limits.PARZELLE
        val parcel = Parcel(
            id = world.nextId("g"),
            gx = gx,
            gy = gy,
            name = json.optString("name").ifBlank { "Unbenanntes Gebiet" }.take(40),
            palette = enumOrNull<Palette>(json.optString("palette")) ?: Palette.MOOS,
        )
        // Früh eintragen: Nahtreparatur und Zusammenhangsprüfung fragen die
        // Karte (outdoor.terrainAt/walkable) — dafür muss die Parzelle drin sein.
        // Ab hier wirft nichts mehr; die Parzelle bleibt nie halb gebaut zurück.
        outdoor.addParcel(parcel)

        // 1. Gelände: Zeilen auffüllen/kürzen, unbekannte Zeichen werden Gras.
        val rows = json.optJSONArray("gelaende")
        for (ly in 0 until g) {
            val row = rows?.optString(ly).orEmpty()
            for (lx in 0 until g) {
                val terrain = Terrain.fromZeichen(row.getOrNull(lx)) ?: Terrain.GRAS
                parcel.setTerrainLocal(lx, ly, terrain)
            }
        }

        // 2. Nähte zu existierenden Nachbarn reparieren (Tore garantieren).
        repairSeams(parcel, outdoor)

        // 3. Mindestanteil begehbaren Geländes erzwingen.
        enforceWalkableShare(parcel)

        // 4. Gebäude: Grundriss klemmen (1 Kachel Rand), Überlappungen verwerfen.
        val doorTiles = mutableSetOf<Pair<Int, Int>>()
        json.optJSONArray("gebaeude")?.let { arr ->
            for (i in 0 until arr.length()) {
                if (parcel.buildingIds.size >= Limits.MAX_GEBAEUDE) break
                val b = arr.optJSONObject(i) ?: continue
                val bw = b.optInt("breite", 2).coerceIn(2, 3)
                val bh = b.optInt("tiefe", 2).coerceIn(2, 3)
                // 1 Kachel Rand, unten 2 (Türkachel + Rand) — blockiert keine Tore.
                val lx = b.optInt("tx", 2).coerceIn(1, g - bw - 1)
                val ly = b.optInt("ty", 2).coerceIn(1, g - bh - 2)
                val neu = Building(
                    id = world.nextId("b"),
                    name = b.optString("name").ifBlank { "Gebäude" }.take(30),
                    tx = parcel.originTx() + lx,
                    ty = parcel.originTy() + ly,
                    w = bw,
                    h = bh,
                    stil = enumOrNull<BuildingStyle>(b.optString("stil")) ?: BuildingStyle.HAUS,
                    colorSlot = b.optInt("farbe", 6).coerceIn(0, SLOT_COLORS.size - 1),
                    locked = b.optBoolean("verschlossen", false),
                    lockHint = b.optString("hinweis").takeIf { it.isNotBlank() && it != "null" }?.take(120),
                )
                // Überlappung mit bereits gesetzten Gebäuden (inkl. Türkacheln)?
                val belegt = outdoor.buildings.values.any { alt ->
                    (neu.tx until neu.tx + neu.w).any { x ->
                        (neu.ty until neu.ty + neu.h + 1).any { y -> alt.covers(x, y) || alt.doorTile() == x to y }
                    }
                } || doorTiles.any { neu.covers(it.first, it.second) }
                if (belegt) continue
                // Eingangskachel freischneiden, falls das Gelände dort blockiert.
                val dt = neu.doorTile()
                if (outdoor.terrainAt(dt.first, dt.second)?.begehbar != true) {
                    parcel.setTerrainLocal(dt.first - parcel.originTx(), dt.second - parcel.originTy(), Terrain.GRAS)
                }
                outdoor.buildings[neu.id] = neu
                parcel.buildingIds.add(neu.id)
                doorTiles.add(dt)
            }
        }

        // 5. Requisiten: Vokabular-Filter, lokal→global, Kollisionen verwerfen.
        json.optJSONArray("requisiten")?.let { arr ->
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                if (parcel.props.size >= Limits.MAX_PROPS_PARZELLE) break
                val type = enumOrNull<PropType>(p.optString("typ")) ?: continue
                val lx = p.optInt("tx", 1).coerceIn(0, g - 1)
                val ly = p.optInt("ty", 1).coerceIn(0, g - 1)
                val tx = parcel.originTx() + lx
                val ty = parcel.originTy() + ly
                if (parcel.terrainLocal(lx, ly).let { !it.begehbar }) continue
                if (outdoor.buildings.values.any { it.covers(tx, ty) }) continue
                if (tx to ty in doorTiles) continue
                if (parcel.props.any { it.tx == tx && it.ty == ty }) continue
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
                parcel.props.add(prop)
            }
        }

        // 6. Figuren: auf freie begehbare Kacheln, roomId = Parzellen-Id.
        json.optJSONArray("figuren")?.let { arr ->
            for (i in 0 until arr.length()) {
                if (parcel.npcIds.size >= Limits.MAX_NPCS_PARZELLE) break
                val f = arr.optJSONObject(i) ?: continue
                val name = f.optString("name").ifBlank { "Fremde" }.take(30)
                var tx = parcel.originTx() + f.optInt("tx", g / 2).coerceIn(0, g - 1)
                var ty = parcel.originTy() + f.optInt("ty", g / 2).coerceIn(0, g - 1)
                if (!tileFreeForNpc(parcel, outdoor, tx, ty, doorTiles)) {
                    val frei = freeParcelTile(parcel, outdoor, doorTiles) ?: continue
                    tx = frei.first
                    ty = frei.second
                }
                val npc = Npc(
                    id = world.nextId("n"),
                    name = name,
                    roomId = parcel.id,
                    tx = tx,
                    ty = ty,
                    look = buildLook(f.optJSONObject("aussehen")),
                    charakterkarte = f.optString("charakterkarte").take(400),
                    rolle = f.optString("rolle").ifBlank { "Bewohner" }.take(40),
                )
                world.addNpc(npc)
                parcel.npcIds.add(npc.id)
            }
        }

        ensureParcelConnectivity(parcel, outdoor)
        return parcel
    }

    /**
     * Nahtreparatur: Wo der Nachbar an der gemeinsamen Kante begehbar ist,
     * garantiert ein Fenster von mindestens [GATE_BREITE] zusammenhängenden
     * Kacheln den Durchgang — notfalls wird auf UNSERER Seite Gras geschnitten
     * (bestehende Parzellen werden nie verändert; dort könnte der Spieler
     * stehen). Ist das Nachbarufer komplett unbegehbar, bleibt die Naht zu.
     */
    private fun repairSeams(parcel: Parcel, outdoor: OutdoorMap) {
        val g = Limits.PARZELLE
        // Richtung: (dgx, dgy) → Kantenzellen als (lokale Zelle, Nachbar-Zelle global)
        val richtungen = listOf(
            Triple(-1, 0) { k: Int -> (0 to k) to (parcel.originTx() - 1 to parcel.originTy() + k) },
            Triple(1, 0) { k: Int -> (g - 1 to k) to (parcel.originTx() + g to parcel.originTy() + k) },
            Triple(0, -1) { k: Int -> (k to 0) to (parcel.originTx() + k to parcel.originTy() - 1) },
            Triple(0, 1) { k: Int -> (k to g - 1) to (parcel.originTx() + k to parcel.originTy() + g) },
        )
        richtungen.forEach { (dgx, dgy, zelle) ->
            outdoor.parcelAt(parcel.gx + dgx, parcel.gy + dgy) ?: return@forEach
            val nachbarBegehbar = BooleanArray(g) { k ->
                val (_, nachbar) = zelle(k)
                outdoor.terrainAt(nachbar.first, nachbar.second)?.begehbar == true &&
                    outdoor.buildings.values.none { it.covers(nachbar.first, nachbar.second) }
            }
            val unsBegehbar = BooleanArray(g) { k ->
                val (lokal, _) = zelle(k)
                parcel.terrainLocal(lokal.first, lokal.second).begehbar
            }
            // Gibt es schon ein gemeinsames Tor ausreichender Breite?
            if (maxRun(BooleanArray(g) { nachbarBegehbar[it] && unsBegehbar[it] }) >= GATE_BREITE) return@forEach
            // Bestes Fenster: GATE_BREITE zusammenhängend begehbare Nachbarzellen,
            // möglichst mittig. Kein solches Fenster → Nachbarufer zu, Naht bleibt zu.
            val fenster = (0..g - GATE_BREITE)
                .filter { start -> (start until start + GATE_BREITE).all { nachbarBegehbar[it] } }
                .minByOrNull { Math.abs(it + GATE_BREITE / 2 - g / 2) }
                ?: return@forEach
            for (k in fenster until fenster + GATE_BREITE) {
                val (lokal, _) = zelle(k)
                if (!parcel.terrainLocal(lokal.first, lokal.second).begehbar) {
                    parcel.setTerrainLocal(lokal.first, lokal.second, Terrain.GRAS)
                }
            }
        }
    }

    private fun maxRun(werte: BooleanArray): Int {
        var best = 0
        var lauf = 0
        werte.forEach { wert ->
            lauf = if (wert) lauf + 1 else 0
            if (lauf > best) best = lauf
        }
        return best
    }

    /** Wandelt unbegehbares Gelände (von hinten beginnend) in Gras, bis der Mindestanteil steht. */
    private fun enforceWalkableShare(parcel: Parcel) {
        val g = Limits.PARZELLE
        val gesamt = g * g
        val minimum = (gesamt * Limits.MIN_BEGEHBAR_ANTEIL).toInt()
        var begehbar = (0 until gesamt).count {
            Terrain.fromZeichen(parcel.terrain[it])?.begehbar == true
        }
        var index = gesamt - 1
        while (begehbar < minimum && index >= 0) {
            if (Terrain.fromZeichen(parcel.terrain[index])?.begehbar != true) {
                parcel.terrain[index] = Terrain.GRAS.zeichen
                begehbar++
            }
            index--
        }
    }

    private fun tileFreeForNpc(
        parcel: Parcel,
        outdoor: OutdoorMap,
        tx: Int,
        ty: Int,
        doorTiles: Set<Pair<Int, Int>>,
    ): Boolean {
        val lx = tx - parcel.originTx()
        val ly = ty - parcel.originTy()
        if (!parcel.terrainLocal(lx, ly).begehbar) return false
        if (outdoor.buildings.values.any { it.covers(tx, ty) }) return false
        if (tx to ty in doorTiles) return false
        return parcel.props.none { it.blocksTile && it.tx == tx && it.ty == ty }
    }

    private fun freeParcelTile(
        parcel: Parcel,
        outdoor: OutdoorMap,
        doorTiles: Set<Pair<Int, Int>>,
    ): Pair<Int, Int>? {
        val g = Limits.PARZELLE
        for (ly in 1 until g - 1) {
            for (lx in 1 until g - 1) {
                val tx = parcel.originTx() + lx
                val ty = parcel.originTy() + ly
                if (tileFreeForNpc(parcel, outdoor, tx, ty, doorTiles)) return tx to ty
            }
        }
        return null
    }

    /**
     * Zusammenhang der Parzelle: Alle Anker (Naht-Tore + Gebäudetüren + das
     * begehbare Parzellenzentrum) müssen sich erreichen können. Das Zentrum
     * zählt mit, damit das Innere — dort landen Requisiten und notfalls das
     * Zielobjekt — nie hinter einer Wasser-/Felswand isoliert bleibt. Zuerst
     * wird notfalls Gelände auf Gras geschnitten (L-Pfad), danach werden —
     * wie in Räumen — blockierende Requisiten begehbar gemacht.
     */
    private fun ensureParcelConnectivity(parcel: Parcel, outdoor: OutdoorMap) {
        val anker = (ankerTiles(parcel, outdoor) + listOfNotNull(zentrumTile(parcel))).distinct()
        if (anker.size < 2) return
        val start = anker.first()

        // Phase 1: Gelände. Begehbarkeit ohne Requisiten (die werden degradiert).
        fun terrainWalkable(tx: Int, ty: Int): Boolean {
            val terrain = outdoor.terrainAt(tx, ty) ?: return false
            return terrain.begehbar && outdoor.buildings.values.none { it.covers(tx, ty) }
        }
        val b = outdoor.bounds()
        anker.drop(1).forEach { zielTile ->
            if (Pathfinder.findPath(b[0], b[1], b[2], b[3], ::terrainWalkable, start, zielTile).isEmpty()) {
                carvePath(parcel, outdoor, start, zielTile)
            }
        }

        // Phase 2: Blocker-Requisiten dieser Parzelle degradieren.
        while (true) {
            val alleErreichbar = anker.drop(1).all { zielTile ->
                zielTile == start ||
                    Pathfinder.findPath(b[0], b[1], b[2], b[3], outdoor::walkable, start, zielTile).isNotEmpty()
            }
            if (alleErreichbar) return
            val blocker = parcel.props.lastOrNull { it.blocksTile } ?: return
            blocker.blocksTile = false
        }
    }

    /** Naht-Tore (beidseitig begehbare Kantenkacheln) + Gebäudetüren der Parzelle. */
    private fun ankerTiles(parcel: Parcel, outdoor: OutdoorMap): List<Pair<Int, Int>> {
        val g = Limits.PARZELLE
        val anker = mutableListOf<Pair<Int, Int>>()
        parcel.buildingIds.mapNotNull { outdoor.buildings[it] }.forEach { anker.add(it.doorTile()) }
        for (ly in 0 until g) {
            for (lx in 0 until g) {
                if (lx != 0 && lx != g - 1 && ly != 0 && ly != g - 1) continue
                val tx = parcel.originTx() + lx
                val ty = parcel.originTy() + ly
                if (!parcel.terrainLocal(lx, ly).begehbar) continue
                val nachbarn = listOf(tx - 1 to ty, tx + 1 to ty, tx to ty - 1, tx to ty + 1)
                val tor = nachbarn.any { (nx, ny) ->
                    outdoor.parcelForTile(nx, ny) != null &&
                        outdoor.parcelForTile(nx, ny) !== parcel &&
                        outdoor.terrainAt(nx, ny)?.begehbar == true
                }
                if (tor) anker.add(tx to ty)
            }
        }
        return anker
    }

    /** Die begehbare Kachel, die der Parzellenmitte am nächsten liegt (global). */
    private fun zentrumTile(parcel: Parcel): Pair<Int, Int>? {
        val g = Limits.PARZELLE
        var best: Pair<Int, Int>? = null
        var bestDist = Int.MAX_VALUE
        for (ly in 0 until g) {
            for (lx in 0 until g) {
                if (!parcel.terrainLocal(lx, ly).begehbar) continue
                val dist = Math.abs(lx - g / 2) + Math.abs(ly - g / 2)
                if (dist < bestDist) {
                    bestDist = dist
                    best = parcel.originTx() + lx to parcel.originTy() + ly
                }
            }
        }
        return best
    }

    /** Schneidet einen L-Pfad aus Gras durch DIESE Parzelle (x zuerst, dann y). */
    private fun carvePath(parcel: Parcel, outdoor: OutdoorMap, von: Pair<Int, Int>, nach: Pair<Int, Int>) {
        fun carve(tx: Int, ty: Int) {
            val lx = tx - parcel.originTx()
            val ly = ty - parcel.originTy()
            if (lx !in 0 until Limits.PARZELLE || ly !in 0 until Limits.PARZELLE) return
            if (outdoor.buildings.values.any { it.covers(tx, ty) }) return
            if (!parcel.terrainLocal(lx, ly).begehbar) parcel.setTerrainLocal(lx, ly, Terrain.GRAS)
        }
        var x = von.first
        var y = von.second
        while (x != nach.first) {
            x += if (nach.first > x) 1 else -1
            carve(x, y)
        }
        while (y != nach.second) {
            y += if (nach.second > y) 1 else -1
            carve(x, y)
        }
    }

    /** Mindestbreite eines Naht-Tors in Kacheln. */
    private const val GATE_BREITE = 3

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
