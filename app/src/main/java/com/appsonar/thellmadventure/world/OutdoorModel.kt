package com.appsonar.thellmadventure.world

import org.json.JSONArray
import org.json.JSONObject

/**
 * Die zusammenhängende Außenwelt: ein globales Kachelgitter aus 12×12-
 * [Parcel]-Parzellen, das beim Aufdecken von Nebelrändern wächst. Alle
 * Positionen (Spieler, Requisiten, Figuren, Gebäude) sind GLOBALE Kacheln —
 * die Start-Parzelle (0,0) belegt die Kacheln 0..11, ihr Westnachbar −12..−1.
 * Dadurch braucht es keinerlei Naht-Sonderfälle: eine walkable(tx,ty)-Lambda
 * über die ganze Karte genügt für Pfadfindung, Treffer-Tests und Malreihenfolge.
 *
 * Gebäude führen über [Building.targetRoomId] in normale [Room]-Innenräume —
 * der bestehende Raum-Flow bleibt dort unverändert.
 */

/** Eine 12×12-Parzelle der Außenwelt. Requisiten/Figuren in GLOBALEN Kacheln. */
class Parcel(
    val id: String,
    val gx: Int,
    val gy: Int,
    var name: String,
    var palette: Palette = Palette.MOOS,
    /** Geländezeichen (siehe [Terrain.zeichen]), Index = ly * PARZELLE + lx. */
    val terrain: CharArray = CharArray(Limits.PARZELLE * Limits.PARZELLE) { Terrain.GRAS.zeichen },
    val props: MutableList<Prop> = mutableListOf(),
    val buildingIds: MutableList<String> = mutableListOf(),
    val npcIds: MutableList<String> = mutableListOf(),
    var melodyId: String? = null,
) {

    /** Gelände an LOKALER Kachel (0..11); außerhalb GRAS (defensiv). */
    fun terrainLocal(lx: Int, ly: Int): Terrain {
        if (lx !in 0 until Limits.PARZELLE || ly !in 0 until Limits.PARZELLE) return Terrain.GRAS
        return Terrain.fromZeichen(terrain[ly * Limits.PARZELLE + lx]) ?: Terrain.GRAS
    }

    fun setTerrainLocal(lx: Int, ly: Int, t: Terrain) {
        if (lx !in 0 until Limits.PARZELLE || ly !in 0 until Limits.PARZELLE) return
        terrain[ly * Limits.PARZELLE + lx] = t.zeichen
    }

    /** Globale Kachel der linken oberen Parzellenecke. */
    fun originTx(): Int = gx * Limits.PARZELLE
    fun originTy(): Int = gy * Limits.PARZELLE

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("gx", gx)
        put("gy", gy)
        put("name", name)
        put("palette", palette.name)
        put("terrain", JSONArray().also { arr ->
            for (ly in 0 until Limits.PARZELLE) {
                arr.put(String(terrain, ly * Limits.PARZELLE, Limits.PARZELLE))
            }
        })
        put("props", JSONArray().also { arr -> props.forEach { arr.put(it.toJson()) } })
        put("buildingIds", JSONArray().also { arr -> buildingIds.forEach { arr.put(it) } })
        put("npcIds", JSONArray().also { arr -> npcIds.forEach { arr.put(it) } })
        put("melodyId", melodyId ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): Parcel? {
            val id = json.optString("id")
            if (id.isBlank()) return null
            val parcel = Parcel(
                id = id,
                gx = json.optInt("gx", 0),
                gy = json.optInt("gy", 0),
                name = json.optString("name", "Gebiet"),
                palette = com.appsonar.thellmadventure.data.enumOrNull<Palette>(json.optString("palette"))
                    ?: Palette.MOOS,
                melodyId = json.optString("melodyId").takeIf { it.isNotBlank() && it != "null" },
            )
            json.optJSONArray("terrain")?.let { arr ->
                for (ly in 0 until Limits.PARZELLE) {
                    val row = arr.optString(ly)
                    for (lx in 0 until Limits.PARZELLE) {
                        val t = Terrain.fromZeichen(row.getOrNull(lx)) ?: Terrain.GRAS
                        parcel.terrain[ly * Limits.PARZELLE + lx] = t.zeichen
                    }
                }
            }
            json.optJSONArray("props")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { Prop.fromJson(it)?.let(parcel.props::add) }
                }
            }
            json.optJSONArray("buildingIds")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let(parcel.buildingIds::add)
                }
            }
            json.optJSONArray("npcIds")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let(parcel.npcIds::add)
                }
            }
            return parcel
        }
    }
}

/**
 * Ein Gebäude auf der Karte. [targetRoomId] null = das Innere ist noch nicht
 * generiert (KI erzeugt es beim ersten Betreten). Die Tür sitzt mittig an der
 * Südfront; [doorTile] ist die begehbare Kachel DAVOR.
 */
class Building(
    val id: String,
    var name: String,
    val tx: Int,
    val ty: Int,
    val w: Int,
    val h: Int,
    var stil: BuildingStyle = BuildingStyle.HAUS,
    var colorSlot: Int = 6,
    var targetRoomId: String? = null,
    var locked: Boolean = false,
    var lockHint: String? = null,
) {

    /** Die begehbare Eingangskachel vor der Südfront. */
    fun doorTile(): Pair<Int, Int> = tx + w / 2 to ty + h

    /** true, wenn die globale Kachel vom Grundriss belegt ist. */
    fun covers(x: Int, y: Int): Boolean = x in tx until tx + w && y in ty until ty + h

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("tx", tx)
        put("ty", ty)
        put("w", w)
        put("h", h)
        put("stil", stil.name)
        put("colorSlot", colorSlot)
        put("target", targetRoomId ?: JSONObject.NULL)
        put("locked", locked)
        put("lockHint", lockHint ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): Building? {
            val id = json.optString("id")
            if (id.isBlank()) return null
            return Building(
                id = id,
                name = json.optString("name", "Gebäude"),
                tx = json.optInt("tx", 0),
                ty = json.optInt("ty", 0),
                w = json.optInt("w", 2).coerceIn(2, 3),
                h = json.optInt("h", 2).coerceIn(2, 3),
                stil = com.appsonar.thellmadventure.data.enumOrNull<BuildingStyle>(json.optString("stil"))
                    ?: BuildingStyle.HAUS,
                colorSlot = json.optInt("colorSlot", 6).coerceIn(0, SLOT_COLORS.size - 1),
                targetRoomId = json.optString("target").takeIf { it.isNotBlank() && it != "null" },
                locked = json.optBoolean("locked", false),
                lockHint = json.optString("lockHint").takeIf { it.isNotBlank() && it != "null" },
            )
        }
    }
}

/** Die wachsende Außenkarte: Parzellen im Raster plus alle Gebäude. */
class OutdoorMap {

    val parcels = LinkedHashMap<String, Parcel>()
    val buildings = LinkedHashMap<String, Building>()

    private val byGrid = HashMap<Pair<Int, Int>, Parcel>()

    fun addParcel(parcel: Parcel) {
        parcels[parcel.id] = parcel
        byGrid[parcel.gx to parcel.gy] = parcel
    }

    fun parcelAt(gx: Int, gy: Int): Parcel? = byGrid[gx to gy]

    fun parcelForTile(tx: Int, ty: Int): Parcel? =
        parcelAt(Math.floorDiv(tx, Limits.PARZELLE), Math.floorDiv(ty, Limits.PARZELLE))

    /** Gelände an GLOBALER Kachel; null = Nebel (Parzelle existiert nicht). */
    fun terrainAt(tx: Int, ty: Int): Terrain? {
        val parcel = parcelForTile(tx, ty) ?: return null
        return parcel.terrainLocal(
            Math.floorMod(tx, Limits.PARZELLE),
            Math.floorMod(ty, Limits.PARZELLE),
        )
    }

    /** Begehbar: begehbares Gelände, kein Gebäudekorpus, keine Blocker-Requisite. */
    fun walkable(tx: Int, ty: Int): Boolean {
        val terrain = terrainAt(tx, ty) ?: return false
        if (!terrain.begehbar) return false
        if (buildings.values.any { it.covers(tx, ty) }) return false
        val prop = propAt(tx, ty)
        return prop == null || !prop.blocksTile
    }

    /** Gebäude, dessen Grundriss die Kachel belegt (fürs Antippen). */
    fun buildingAt(tx: Int, ty: Int): Building? =
        buildings.values.firstOrNull { it.covers(tx, ty) }

    /** Gebäude, dessen Eingangskachel das ist (Betreten beim Daraufstehen). */
    fun buildingDoorAt(tx: Int, ty: Int): Building? =
        buildings.values.firstOrNull { it.doorTile() == tx to ty }

    fun propAt(tx: Int, ty: Int): Prop? =
        parcelForTile(tx, ty)?.props?.firstOrNull { it.tx == tx && it.ty == ty }

    /**
     * Nebelrand-Prüfung: Ist (tx,ty) eine begehbare Randkachel, hinter der
     * KEINE Parzelle liegt, kommt das fehlende Nachbar-Raster (gx,gy) zurück.
     */
    fun frontierAt(tx: Int, ty: Int): Pair<Int, Int>? {
        val parcel = parcelForTile(tx, ty) ?: return null
        if (!walkable(tx, ty)) return null
        val lx = Math.floorMod(tx, Limits.PARZELLE)
        val ly = Math.floorMod(ty, Limits.PARZELLE)
        val kandidaten = listOfNotNull(
            if (lx == 0) parcel.gx - 1 to parcel.gy else null,
            if (lx == Limits.PARZELLE - 1) parcel.gx + 1 to parcel.gy else null,
            if (ly == 0) parcel.gx to parcel.gy - 1 else null,
            if (ly == Limits.PARZELLE - 1) parcel.gx to parcel.gy + 1 else null,
        )
        return kandidaten.firstOrNull { (gx, gy) -> parcelAt(gx, gy) == null }
    }

    /** Kachelgrenzen aller Parzellen: [minTx, minTy, maxTxExkl, maxTyExkl]. */
    fun bounds(): IntArray {
        if (parcels.isEmpty()) return intArrayOf(0, 0, Limits.PARZELLE, Limits.PARZELLE)
        val minGx = parcels.values.minOf { it.gx }
        val maxGx = parcels.values.maxOf { it.gx }
        val minGy = parcels.values.minOf { it.gy }
        val maxGy = parcels.values.maxOf { it.gy }
        return intArrayOf(
            minGx * Limits.PARZELLE,
            minGy * Limits.PARZELLE,
            (maxGx + 1) * Limits.PARZELLE,
            (maxGy + 1) * Limits.PARZELLE,
        )
    }

    fun allProps(): Sequence<Prop> = parcels.values.asSequence().flatMap { it.props.asSequence() }

    fun toJson(): JSONObject = JSONObject().apply {
        put("parcels", JSONArray().also { arr -> parcels.values.forEach { arr.put(it.toJson()) } })
        put("buildings", JSONArray().also { arr -> buildings.values.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject?): OutdoorMap? {
            if (json == null) return null
            val map = OutdoorMap()
            json.optJSONArray("parcels")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { Parcel.fromJson(it)?.let(map::addParcel) }
                }
            }
            json.optJSONArray("buildings")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { obj ->
                        Building.fromJson(obj)?.let { map.buildings[it.id] = it }
                    }
                }
            }
            return map
        }
    }
}
