package com.castigaro.thellmadventure.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Picture
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.castigaro.thellmadventure.game.Pathfinder
import com.castigaro.thellmadventure.world.Building
import com.castigaro.thellmadventure.world.Door
import com.castigaro.thellmadventure.world.Limits
import com.castigaro.thellmadventure.world.Look
import com.castigaro.thellmadventure.world.Npc
import com.castigaro.thellmadventure.world.OutdoorMap
import com.castigaro.thellmadventure.world.Palette
import com.castigaro.thellmadventure.world.Parcel
import com.castigaro.thellmadventure.world.Prop
import com.castigaro.thellmadventure.world.Room
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Die isometrische Spielfläche. Wie Kap-Krakenzahns SceneView eine reine
 * View/Canvas-Lösung — aber mit Kachel-Pfadfindung und einer Kamera, sodass
 * Räume größer als das Display sein können: Tippen = laufen/interagieren,
 * Ziehen = Ansicht schwenken.
 *
 * Zwei Szenen teilen sich dieselbe Mechanik: ein [Room] (Innenraum, lokale
 * Kacheln ab 0,0) oder die [OutdoorMap] (Außenkarte, GLOBALE Kacheln, auch
 * negativ). Dank der linearen Iso-Mathematik brauchen Pfadfindung,
 * Treffer-Tests und Malreihenfolge keinerlei Sonderfälle — nur Begehbarkeit
 * und Grenzen kommen aus der jeweiligen Szene.
 *
 * Performance: Das statische Gelände (Boden, Wände bzw. Parzellen-Terrain)
 * wird je Ort einmal in ein [Picture] aufgenommen und danach nur noch
 * abgespielt; die Animationsschleife tickt gestuft ([FrameGate]) — volle
 * Bildrate nur bei Bewegung, gemächlicher Takt für sichtbares Geschimmer,
 * völliger Stillstand sonst.
 */
class IsoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        /** Spieler ist bei einer Requisite angekommen. */
        fun onPropReached(prop: Prop)

        /** Spieler steht auf einer Türkachel (Innenraum). */
        fun onDoorReached(door: Door)

        /** Spieler ist bei einer Figur angekommen. */
        fun onNpcReached(npc: Npc) {}

        /** Spieler steht vor der Eingangstür eines Gebäudes (Außenkarte). */
        fun onBuildingReached(building: Building) {}

        /** Spieler steht an einem Nebelrand — dahinter fehlt Parzelle (gx,gy). */
        fun onFrontierReached(gx: Int, gy: Int) {}

        /** Leere Kachel angetippt (nur Laufen). */
        fun onWalkOnly() {}

        /** Ziel ist unerreichbar (kein Pfad). */
        fun onBlockedTarget() {}

        /** Kamera-Schwenkmodus betreten/verlassen (für den „Zentrieren"-Chip). */
        fun onPanModeChanged(panning: Boolean) {}
    }

    /** Wohin der aktuelle Lauf führt — wird bei Ankunft aufgelöst. */
    private sealed interface Ziel {
        class ZuProp(val prop: Prop) : Ziel
        class ZuDoor(val door: Door) : Ziel
        class ZuNpc(val npc: Npc) : Ziel
        class ZuGebaeude(val building: Building) : Ziel
        class ZuNebel(val gx: Int, val gy: Int) : Ziel
    }

    private sealed interface Szene {
        class Raum(val room: Room) : Szene
        class Karte(val map: OutdoorMap) : Szene
    }

    var listener: Listener? = null

    val camera = Camera()

    /** Aussehen der Spielfigur (aus dem Vokabular; später aus der Story). */
    var playerLook = Look()

    /** Nachschlagefunktion für Figuren (aus dem WorldState). */
    var npcProvider: (String) -> Npc? = { null }

    private var szene: Szene? = null

    // Spielerposition in kontinuierlichen Kachel-Koordinaten (Mitte = i+0.5).
    private var px = 0.5f
    private var py = 0.5f

    private val path = ArrayDeque<Pair<Int, Int>>()
    private var ziel: Ziel? = null
    private var facingRight = true
    private var walkPhase = 0f
    private var lastFrame = 0L
    private val startTime = System.currentTimeMillis()

    private val walkSpeed = 3.2f // Kacheln pro Sekunde

    // Picture-Caches des statischen Untergrunds (Terrain bzw. Boden+Wände).
    // In-Memory, an die gebundene Szene geknüpft — nie serialisiert.
    private class CachedPicture(val picture: Picture, val offX: Float, val offY: Float)

    private val parcelPictures = HashMap<String, CachedPicture>()
    private var parcelCacheStand = -1
    private var roomPicture: CachedPicture? = null

    private val ambientTick = Runnable { postInvalidateOnAnimation() }

    // Touch-Unterscheidung Tipp vs. Schwenken vs. Pinch-Zoom
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var panning = false
    private var multiTouch = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                camera.scaleBy(detector.scaleFactor, detector.focusX, detector.focusY)
                clampCamera()
                listener?.onPanModeChanged(true)
                postInvalidateOnAnimation()
                return true
            }
        },
    )

    /** Erlaubt der Activity, die Animationsschleife bei onPause anzuhalten. */
    var animate: Boolean = true
        set(value) {
            field = value
            if (value) postInvalidateOnAnimation() else removeCallbacks(ambientTick)
        }

    /** Bindet einen Raum und setzt den Spieler ohne Animation auf [startTile]. */
    fun bind(room: Room, startTile: Pair<Int, Int>) {
        bindSzene(Szene.Raum(room), startTile)
    }

    /** Bindet die Außenkarte; [startTile] ist eine GLOBALE Kachel. */
    fun bindKarte(map: OutdoorMap, startTile: Pair<Int, Int>) {
        bindSzene(Szene.Karte(map), startTile)
    }

    /** Wirft die Terrain-Pictures weg (nach dem Manifestieren neuer Parzellen). */
    fun kartenCacheLeeren() {
        parcelPictures.clear()
        parcelCacheStand = -1
        invalidate()
    }

    private fun bindSzene(neu: Szene, startTile: Pair<Int, Int>) {
        szene = neu
        px = startTile.first + 0.5f
        py = startTile.second + 0.5f
        path.clear()
        ziel = null
        parcelPictures.clear()
        parcelCacheStand = -1
        roomPicture = null
        camera.jumpTo(IsoMath.worldX(px, py), IsoMath.worldY(px, py))
        clampCamera()
        listener?.onPanModeChanged(false)
        invalidate()
    }

    fun playerTile(): Pair<Int, Int> = floor(px).toInt() to floor(py).toInt()

    /** Follow-Modus wieder aktivieren (Chip „Zentrieren"). */
    fun recenterCamera() {
        camera.recenter()
        listener?.onPanModeChanged(false)
        postInvalidateOnAnimation()
    }

    // ---- Szenen-Zugriffe -------------------------------------------------------

    private fun walkableAt(tx: Int, ty: Int): Boolean = when (val s = szene) {
        is Szene.Raum -> s.room.walkable(tx, ty)
        is Szene.Karte -> s.map.walkable(tx, ty)
        null -> false
    }

    /** Pfadgrenzen [minX, minY, maxXExkl, maxYExkl] der aktuellen Szene. */
    private fun pathBounds(): IntArray = when (val s = szene) {
        is Szene.Raum -> intArrayOf(0, 0, s.room.w, s.room.h)
        is Szene.Karte -> s.map.bounds()
        null -> intArrayOf(0, 0, 0, 0)
    }

    private fun clampCamera() {
        when (val s = szene) {
            is Szene.Raum -> camera.clampToRoom(s.room.w, s.room.h)
            is Szene.Karte -> {
                val b = s.map.bounds()
                camera.clampToBounds(
                    IsoMath.boundsMinX(b[0], b[3]),
                    IsoMath.boundsMaxX(b[2], b[1]),
                    IsoMath.boundsMinY(b[0], b[1]) - IsoMath.WALL_H,
                    IsoMath.boundsMaxY(b[2], b[3]),
                )
            }
            null -> {}
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        camera.viewW = w.toFloat()
        camera.viewH = h.toFloat()
        // Basiszoom = Displaydichte; ein bereits gepinchter Faktor bleibt erhalten.
        val relative = if (camera.baseZoom > 0f) camera.zoom / camera.baseZoom else 1f
        camera.baseZoom = resources.displayMetrics.density
        camera.zoom = camera.baseZoom * relative.coerceIn(Camera.MIN_SCALE, Camera.MAX_SCALE)
        clampCamera()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x; downY = event.y
                lastX = event.x; lastY = event.y
                panning = false
                multiTouch = false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // Ab dem zweiten Finger zählt die Geste als Pinch — kein Tap,
                // kein Ein-Finger-Schwenk mehr bis zum nächsten ACTION_DOWN.
                multiTouch = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (multiTouch || event.pointerCount > 1) {
                    lastX = event.x; lastY = event.y
                    return true
                }
                if (!panning && hypot(event.x - downX, event.y - downY) > touchSlop) {
                    panning = true
                    listener?.onPanModeChanged(true)
                }
                if (panning) {
                    camera.panBy(event.x - lastX, event.y - lastY)
                    clampCamera()
                    postInvalidateOnAnimation()
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (!panning && !multiTouch) handleTap(event.x, event.y)
                // Finger weg: Geste beendet, die Schleife darf zur Ruhe kommen.
                panning = false
                multiTouch = false
            }
        }
        return true
    }

    private fun handleTap(sx: Float, sy: Float) {
        val s = szene ?: return
        val wx = camera.worldXFromScreen(sx)
        val wy = camera.worldYFromScreen(sy)

        // Tippen beendet den Schwenkmodus — Kamera folgt wieder.
        camera.recenter()
        listener?.onPanModeChanged(false)

        when (s) {
            is Szene.Raum -> tapRaum(s.room, wx, wy)
            is Szene.Karte -> tapKarte(s.map, wx, wy)
        }
    }

    private fun tapRaum(r: Room, wx: Float, wy: Float) {
        // Figuren zuerst (stehen „vor" gleichrangigen Requisiten). Bei mehreren
        // Treffern gewinnt nicht das vorderste, sondern das dem Tipp-Punkt
        // nächstgelegene — sonst schluckt eine vordere Requisite mit ihrer
        // hohen Trefferbox Taps auf Dinge/Kacheln dahinter.
        val npc = r.npcIds.mapNotNull { npcProvider(it) }
            .filter { hitsTile(it.tx, it.ty, wx, wy) }
            .minByOrNull { hitDistance(it.tx, it.ty, wx, wy) }
        if (npc != null) {
            approachNpc(npc)
            return
        }

        val prop = r.props
            .filter { hitsTile(it.tx, it.ty, wx, wy) }
            .minByOrNull { hitDistance(it.tx, it.ty, wx, wy) }
        if (prop != null) {
            approachProp(prop)
            return
        }

        val tile = IsoMath.pickTile(wx, wy, r.w, r.h) ?: return
        val door = r.doorAt(tile.first, tile.second)
        walkTo(tile, if (door != null) Ziel.ZuDoor(door) else null)
    }

    private fun tapKarte(map: OutdoorMap, wx: Float, wy: Float) {
        // Schnappschüsse: Die Parzellen-Generierung mutiert die Karte auf dem
        // IO-Thread — Listenkopien halten die Treffersuche stabil.
        val parcelListe = map.parcels.values.toList()
        val gebaeudeListe = map.buildings.values.toList()

        val npc = parcelListe.asSequence()
            .flatMap { it.npcIds.asSequence() }.mapNotNull { npcProvider(it) }
            .filter { hitsTile(it.tx, it.ty, wx, wy) }
            .minByOrNull { hitDistance(it.tx, it.ty, wx, wy) }
        if (npc != null) {
            approachNpc(npc)
            return
        }

        val prop = parcelListe.asSequence().flatMap { it.props.asSequence() }
            .filter { hitsTile(it.tx, it.ty, wx, wy) }
            .minByOrNull { hitDistance(it.tx, it.ty, wx, wy) }
        if (prop != null) {
            approachProp(prop)
            return
        }

        val building = gebaeudeListe.firstOrNull { hitsBuilding(it, wx, wy) }
        if (building != null) {
            approachBuilding(building)
            return
        }

        val tile = IsoMath.pickTileUnbounded(wx, wy)
        if (map.parcelForTile(tile.first, tile.second) == null) {
            // Tap in den Nebel: zum nächsten Torrand laufen und Land aufdecken.
            approachFog(map, tile)
            return
        }
        map.buildingDoorAt(tile.first, tile.second)?.let { b ->
            approachBuilding(b)
            return
        }
        val frontier = map.frontierAt(tile.first, tile.second)
        walkTo(tile, frontier?.let { Ziel.ZuNebel(it.first, it.second) })
    }

    /** Trefferbox einer Entität auf Kachel (tx,ty) in Weltkoordinaten (inkl. Höhe). */
    private fun hitsTile(tx: Int, ty: Int, wx: Float, wy: Float, heightTiles: Float = HIT_HEIGHT): Boolean {
        val cx = IsoMath.tileCenterX(tx, ty)
        val cy = IsoMath.tileCenterY(tx, ty)
        return abs(wx - cx) <= IsoMath.TILE_W * 0.35f &&
            wy >= cy - IsoMath.TILE_H * heightTiles && wy <= cy + IsoMath.TILE_H * 0.45f
    }

    /** Abstand des Tipp-Punkts zur sichtbaren Sprite-Mitte einer Entität. */
    private fun hitDistance(tx: Int, ty: Int, wx: Float, wy: Float): Float {
        val cx = IsoMath.tileCenterX(tx, ty)
        val cy = IsoMath.tileCenterY(tx, ty) - IsoMath.TILE_H * 0.5f
        return hypot(wx - cx, wy - cy)
    }

    /** Trefferbox eines Gebäudes: alle Grundriss-Kacheln, erhöht um den Körper. */
    private fun hitsBuilding(b: Building, wx: Float, wy: Float): Boolean {
        val heightTiles = MapRenderer.gesamtHoehe(b.stil) / IsoMath.TILE_H + 0.5f
        for (ty in b.ty until b.ty + b.h) {
            for (tx in b.tx until b.tx + b.w) {
                if (hitsTile(tx, ty, wx, wy, heightTiles)) return true
            }
        }
        return false
    }

    // ---- Laufziele -------------------------------------------------------------

    private fun approachNpc(npc: Npc) {
        val b = pathBounds()
        val adj = Pathfinder.findPathAdjacent(b[0], b[1], b[2], b[3], ::walkableAt, playerTile(), npc.tx to npc.ty)
        if (adj == null) {
            ziel = null
            listener?.onBlockedTarget()
            return
        }
        ziel = Ziel.ZuNpc(npc)
        walkPath(adj, immediate = adj.isEmpty())
    }

    private fun approachProp(prop: Prop) {
        val b = pathBounds()
        if (!prop.blocksTile) {
            // Begehbare Requisite (z. B. Teppich): direkt auf die Kachel laufen.
            ziel = Ziel.ZuProp(prop)
            walkPath(
                Pathfinder.findPath(b[0], b[1], b[2], b[3], ::walkableAt, playerTile(), prop.tx to prop.ty),
                immediate = playerTile() == prop.tx to prop.ty,
            )
            return
        }
        val adj = Pathfinder.findPathAdjacent(b[0], b[1], b[2], b[3], ::walkableAt, playerTile(), prop.tx to prop.ty)
        if (adj == null) {
            ziel = null
            listener?.onBlockedTarget()
            return
        }
        ziel = Ziel.ZuProp(prop)
        walkPath(adj, immediate = adj.isEmpty())
    }

    private fun approachBuilding(building: Building) {
        val b = pathBounds()
        val doorTile = building.doorTile()
        if (playerTile() == doorTile) {
            ziel = Ziel.ZuGebaeude(building)
            arrive()
            return
        }
        val p = Pathfinder.findPath(b[0], b[1], b[2], b[3], ::walkableAt, playerTile(), doorTile)
        if (p.isEmpty()) {
            ziel = null
            listener?.onBlockedTarget()
            return
        }
        ziel = Ziel.ZuGebaeude(building)
        walkPath(p, immediate = false)
    }

    /** Tap in den Nebel: zur nächstgelegenen Torkachel am fehlenden Raster laufen. */
    private fun approachFog(map: OutdoorMap, fogTile: Pair<Int, Int>) {
        val gx = Math.floorDiv(fogTile.first, Limits.PARZELLE)
        val gy = Math.floorDiv(fogTile.second, Limits.PARZELLE)
        if (map.parcelAt(gx, gy) != null) return

        // Alle begehbaren Randkacheln existierender Nachbarn, die an (gx,gy) grenzen
        val gates = mutableListOf<Pair<Int, Int>>()
        map.parcelAt(gx - 1, gy)?.let { p ->
            val tx = p.originTx() + Limits.PARZELLE - 1
            for (ly in 0 until Limits.PARZELLE) {
                val t = p.originTy() + ly
                if (map.walkable(tx, t)) gates.add(tx to t)
            }
        }
        map.parcelAt(gx + 1, gy)?.let { p ->
            val tx = p.originTx()
            for (ly in 0 until Limits.PARZELLE) {
                val t = p.originTy() + ly
                if (map.walkable(tx, t)) gates.add(tx to t)
            }
        }
        map.parcelAt(gx, gy - 1)?.let { p ->
            val ty = p.originTy() + Limits.PARZELLE - 1
            for (lx in 0 until Limits.PARZELLE) {
                val t = p.originTx() + lx
                if (map.walkable(t, ty)) gates.add(t to ty)
            }
        }
        map.parcelAt(gx, gy + 1)?.let { p ->
            val ty = p.originTy()
            for (lx in 0 until Limits.PARZELLE) {
                val t = p.originTx() + lx
                if (map.walkable(t, ty)) gates.add(t to ty)
            }
        }
        if (gates.isEmpty()) {
            listener?.onBlockedTarget()
            return
        }

        val start = playerTile()
        if (start in gates) {
            ziel = Ziel.ZuNebel(gx, gy)
            arrive()
            return
        }
        val b = pathBounds()
        val sortiert = gates.sortedBy { abs(it.first - fogTile.first) + abs(it.second - fogTile.second) }
        for (gate in sortiert) {
            val p = Pathfinder.findPath(b[0], b[1], b[2], b[3], ::walkableAt, start, gate)
            if (p.isNotEmpty()) {
                ziel = Ziel.ZuNebel(gx, gy)
                walkPath(p, immediate = false)
                return
            }
        }
        listener?.onBlockedTarget()
    }

    private fun walkTo(tile: Pair<Int, Int>, zielAufKachel: Ziel?) {
        ziel = zielAufKachel
        if (tile == playerTile()) {
            if (ziel == null) listener?.onWalkOnly() else arrive()
            return
        }
        val b = pathBounds()
        val p = Pathfinder.findPath(b[0], b[1], b[2], b[3], ::walkableAt, playerTile(), tile)
        if (p.isEmpty()) {
            // Blockierte/unerreichbare Kachel: so nah wie möglich heranlaufen,
            // statt sofort „Da kommst du nicht hin" zu melden.
            ziel = null
            val adj = Pathfinder.findPathAdjacent(b[0], b[1], b[2], b[3], ::walkableAt, playerTile(), tile)
            if (adj == null) {
                listener?.onBlockedTarget()
                return
            }
            if (adj.isEmpty()) return // steht bereits direkt daneben
            walkPath(adj, immediate = false)
            return
        }
        walkPath(p, immediate = false)
        if (zielAufKachel == null) listener?.onWalkOnly()
    }

    private fun walkPath(newPath: List<Pair<Int, Int>>, immediate: Boolean) {
        path.clear()
        if (immediate) {
            arrive()
            return
        }
        if (newPath.isEmpty()) {
            ziel = null
            listener?.onBlockedTarget()
            return
        }
        path.addAll(newPath)
        lastFrame = System.currentTimeMillis()
        postInvalidateOnAnimation()
    }

    private fun arrive() {
        when (val z = ziel) {
            is Ziel.ZuProp -> {
                ziel = null
                listener?.onPropReached(z.prop)
            }
            is Ziel.ZuNpc -> {
                ziel = null
                listener?.onNpcReached(z.npc)
            }
            is Ziel.ZuDoor -> {
                ziel = null
                listener?.onDoorReached(z.door)
            }
            is Ziel.ZuGebaeude -> {
                ziel = null
                listener?.onBuildingReached(z.building)
            }
            is Ziel.ZuNebel -> {
                ziel = null
                listener?.onFrontierReached(z.gx, z.gy)
            }
            null -> {}
        }
    }

    // ---- Zeichnen ---------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        val s = szene ?: return
        val now = System.currentTimeMillis()
        val timeMs = now - startTime

        // Hintergrund im Bildschirmraum
        val pal = when (s) {
            is Szene.Raum -> s.room.palette
            is Szene.Karte -> s.map.parcelForTile(playerTile().first, playerTile().second)?.palette
                ?: Palette.MOOS
        }
        PaintKit.verticalGradient(
            canvas, 0f, 0f, width.toFloat(), height.toFloat(),
            pal.bgTop, pal.bgBottom,
        )

        // Bewegung fortschreiben
        val dt = if (lastFrame == 0L) 0f else (now - lastFrame).coerceAtMost(64L) / 1000f
        lastFrame = now
        stepMovement(dt)

        // Kamera folgt dem Spieler; merken, ob sie sich diesen Frame bewegt hat.
        val camXVorher = camera.x
        val camYVorher = camera.y
        camera.follow(IsoMath.worldX(px, py), IsoMath.worldY(px, py), dt)
        clampCamera()
        val kameraRuht = abs(camera.x - camXVorher) < 0.05f && abs(camera.y - camYVorher) < 0.05f

        canvas.save()
        canvas.translate(camera.viewW / 2f, camera.viewH / 2f)
        canvas.scale(camera.zoom, camera.zoom)
        canvas.translate(-camera.x, -camera.y)

        val labelScale = if (camera.zoom > 0f && camera.baseZoom > 0f) {
            (camera.baseZoom / camera.zoom).coerceAtLeast(1f)
        } else 1f

        val ambientSichtbar = when (s) {
            is Szene.Raum -> drawRaum(canvas, s.room, timeMs, labelScale)
            is Szene.Karte -> drawKarte(canvas, s.map, timeMs, labelScale)
        }

        canvas.restore()

        scheduleNext(kameraRuht, ambientSichtbar)
    }

    /** Gestufte Animationsschleife: vsync → Ambient-Takt → Stillstand. */
    private fun scheduleNext(kameraRuht: Boolean, ambientSichtbar: Boolean) {
        removeCallbacks(ambientTick)
        if (!animate) return
        val bewegung = path.isNotEmpty() || panning || multiTouch
        when (FrameGate.modus(bewegung, kameraRuht, ambientSichtbar)) {
            FrameGate.Modus.VSYNC -> postInvalidateOnAnimation()
            FrameGate.Modus.AMBIENT -> postDelayed(ambientTick, AMBIENT_MS)
            FrameGate.Modus.IDLE -> {}
        }
    }

    /** true, wenn Animiertes sichtbar ist (hält den Ambient-Takt wach). */
    private fun drawRaum(canvas: Canvas, r: Room, timeMs: Long, labelScale: Float): Boolean {
        // Statischer Untergrund (Boden, Türmatten, Wände) aus dem Picture
        val cached = roomPicture ?: recordRoom(r).also { roomPicture = it }
        canvas.save()
        canvas.translate(cached.offX, cached.offY)
        canvas.drawPicture(cached.picture)
        canvas.restore()

        // Entities in Painter-Reihenfolge (Requisiten + Figuren + Spieler)
        var animiert = false
        val walking = path.isNotEmpty()
        val entities = ArrayList<Pair<Float, () -> Unit>>(r.props.size + r.npcIds.size + 1)
        r.props.forEach { p ->
            if (FrameGate.propAnimiert(p)) animiert = true
            entities.add((p.tx + p.ty + 0.9f) to { PropPainter.draw(canvas, p, r.palette, timeMs) })
        }
        r.npcIds.mapNotNull { npcProvider(it) }.forEach { npc ->
            entities.add((npc.tx + npc.ty + 1f) to {
                CharacterPainter.draw(
                    canvas, npc.look,
                    IsoMath.tileCenterX(npc.tx, npc.ty), IsoMath.tileCenterY(npc.tx, npc.ty),
                    0f, facingRight = false, walking = false,
                )
            })
        }
        entities.add((px + py) to {
            CharacterPainter.draw(
                canvas, playerLook,
                IsoMath.worldX(px, py), IsoMath.worldY(px, py),
                walkPhase, facingRight, walking,
            )
        })
        entities.sortBy { it.first }
        entities.forEach { it.second() }

        // Türnamen zuletzt: nichts darf sie verdecken. Beim Ranzoomen wachsen
        // sie mit der Welt mit (scale=1); nur beim Rauszoomen wird kompensiert,
        // damit sie am Bildschirm nie unter die lesbare Größe schrumpfen.
        RoomRenderer.drawDoorLabels(canvas, r, timeMs, labelScale)

        // Unentdeckte Türen schimmern — das braucht den Ambient-Takt.
        return animiert || r.doors.any { it.targetRoomId == null }
    }

    /** true, wenn Animiertes sichtbar ist (hält den Ambient-Takt wach). */
    private fun drawKarte(canvas: Canvas, map: OutdoorMap, timeMs: Long, labelScale: Float): Boolean {
        // Sichtbarer Kachelbereich aus dem Kamera-Ausschnitt (Frustum-Culling)
        val halfW = camera.viewW / (2f * camera.zoom)
        val halfH = camera.viewH / (2f * camera.zoom)
        val range = IsoMath.visibleTileRange(
            camera.x - halfW, camera.y - halfH,
            camera.x + halfW, camera.y + halfH,
        )

        // Schnappschüsse: Die Parzellen-Generierung mutiert die Karte auf dem
        // IO-Thread. Neue Parzellen invalidieren zudem die Nachbar-Pictures
        // (Säume/Schaum an der Naht ändern sich mit dem neuen Nachbarn).
        val parcelListe = map.parcels.values.toList()
        if (parcelListe.size != parcelCacheStand) {
            parcelPictures.clear()
            parcelCacheStand = parcelListe.size
        }

        val sichtbareParzellen = parcelListe.filter { parcel ->
            val ox = parcel.originTx()
            val oy = parcel.originTy()
            ox + Limits.PARZELLE >= range[0] && ox <= range[2] &&
                oy + Limits.PARZELLE >= range[1] && oy <= range[3]
        }

        // Statisches Terrain aus den Pictures
        sichtbareParzellen.forEach { parcel ->
            val cached = parcelPictures.getOrPut(parcel.id) { recordParcel(map, parcel) }
            canvas.save()
            canvas.translate(cached.offX, cached.offY)
            canvas.drawPicture(cached.picture)
            canvas.restore()
        }

        val wasser = MapRenderer.drawWasserGlanz(canvas, map, range, timeMs)
        val nebel = MapRenderer.drawFog(canvas, map, range, timeMs)

        var animiert = false
        val walking = path.isNotEmpty()
        val entities = ArrayList<Pair<Float, () -> Unit>>()
        sichtbareParzellen.forEach { parcel ->
            parcel.props.forEach { p ->
                if (FrameGate.propAnimiert(p)) animiert = true
                entities.add((p.tx + p.ty + 0.9f) to { PropPainter.draw(canvas, p, parcel.palette, timeMs) })
            }
            parcel.npcIds.mapNotNull { npcProvider(it) }.forEach { npc ->
                entities.add((npc.tx + npc.ty + 1f) to {
                    CharacterPainter.draw(
                        canvas, npc.look,
                        IsoMath.tileCenterX(npc.tx, npc.ty), IsoMath.tileCenterY(npc.tx, npc.ty),
                        0f, facingRight = false, walking = false,
                    )
                })
            }
        }
        map.buildings.values.toList().forEach { b ->
            if (b.tx + b.w < range[0] || b.tx > range[2] || b.ty + b.h < range[1] || b.ty > range[3]) return@forEach
            // Tiefe der vorderen Ecke, damit Figuren vor der Fassade sichtbar bleiben
            entities.add((b.tx + b.w - 1 + b.ty + b.h - 1 + 0.95f) to {
                MapRenderer.drawBuilding(canvas, b, timeMs)
            })
        }
        entities.add((px + py) to {
            CharacterPainter.draw(
                canvas, playerLook,
                IsoMath.worldX(px, py), IsoMath.worldY(px, py),
                walkPhase, facingRight, walking,
            )
        })
        entities.sortBy { it.first }
        entities.forEach { it.second() }

        val gebaeudeSchimmer = MapRenderer.drawBuildingLabels(canvas, map, range, timeMs, labelScale)
        return wasser || nebel || animiert || gebaeudeSchimmer
    }

    /** Nimmt das statische Terrain einer Parzelle in ein Picture auf. */
    private fun recordParcel(map: OutdoorMap, parcel: Parcel): CachedPicture {
        val g = Limits.PARZELLE
        val minWx = IsoMath.boundsMinX(parcel.originTx(), parcel.originTy() + g)
        val maxWx = IsoMath.boundsMaxX(parcel.originTx() + g, parcel.originTy())
        val minWy = IsoMath.boundsMinY(parcel.originTx(), parcel.originTy())
        val maxWy = IsoMath.boundsMaxY(parcel.originTx() + g, parcel.originTy() + g)
        val pad = IsoMath.TILE_H
        val picture = Picture()
        val c = picture.beginRecording(
            ceil(maxWx - minWx + 2 * pad).toInt(),
            ceil(maxWy - minWy + 2 * pad).toInt(),
        )
        c.translate(-minWx + pad, -minWy + pad)
        MapRenderer.drawParcelStatic(c, map, parcel)
        picture.endRecording()
        return CachedPicture(picture, minWx - pad, minWy - pad)
    }

    /** Nimmt Boden, Türmatten und Wände eines Raums in ein Picture auf. */
    private fun recordRoom(r: Room): CachedPicture {
        val minWx = IsoMath.roomMinX(r.h)
        val maxWx = IsoMath.roomMaxX(r.w)
        val minWy = IsoMath.roomMinY()
        val maxWy = IsoMath.roomMaxY(r.w, r.h)
        val pad = IsoMath.TILE_H
        val picture = Picture()
        val c = picture.beginRecording(
            ceil(maxWx - minWx + 2 * pad).toInt(),
            ceil(maxWy - minWy + 2 * pad).toInt(),
        )
        c.translate(-minWx + pad, -minWy + pad)
        RoomRenderer.drawFloor(c, r)
        RoomRenderer.drawDoorMarkers(c, r)
        RoomRenderer.drawWalls(c, r, 0L)
        picture.endRecording()
        return CachedPicture(picture, minWx - pad, minWy - pad)
    }

    private companion object {
        /** Vertikale Ausdehnung der Trefferbox in Kachelhöhen (Sprites sind hoch). */
        const val HIT_HEIGHT = 1.15f

        /** Takt des Ambient-Modus (Schimmern/Wasser) in Millisekunden. */
        const val AMBIENT_MS = 120L
    }

    private fun stepMovement(dt: Float) {
        val next = path.firstOrNull() ?: return
        val tcx = next.first + 0.5f
        val tcy = next.second + 0.5f
        val dx = tcx - px
        val dy = tcy - py
        val dist = hypot(dx, dy)
        val step = walkSpeed * dt
        walkPhase += dt * 10f

        // Blickrichtung aus der Bewegungsrichtung in Welt-x ableiten
        val worldDx = IsoMath.worldX(tcx, tcy) - IsoMath.worldX(px, py)
        if (abs(worldDx) > 0.01f) facingRight = worldDx >= 0f

        if (dist <= step || dist < 0.001f) {
            px = tcx
            py = tcy
            path.removeFirst()
            if (path.isEmpty()) arrive()
        } else {
            px += dx / dist * step
            py += dy / dist * step
        }
    }
}
