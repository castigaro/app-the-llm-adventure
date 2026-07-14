package com.castigaro.thellmadventure.render

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewConfiguration
import com.castigaro.thellmadventure.game.Pathfinder
import com.castigaro.thellmadventure.world.Door
import com.castigaro.thellmadventure.world.Look
import com.castigaro.thellmadventure.world.Npc
import com.castigaro.thellmadventure.world.Prop
import com.castigaro.thellmadventure.world.Room
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * Die isometrische Spielfläche. Wie Kap-Krakenzahns SceneView eine reine
 * View/Canvas-Lösung mit vsync-getriebener Animationsschleife — aber mit
 * Kachel-Pfadfindung und einer Kamera, sodass Räume größer als das Display
 * sein können: Tippen = laufen/interagieren, Ziehen = Ansicht schwenken.
 */
class IsoView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    interface Listener {
        /** Spieler ist bei einer Requisite angekommen. */
        fun onPropReached(prop: Prop)

        /** Spieler steht auf einer Türkachel. */
        fun onDoorReached(door: Door)

        /** Spieler ist bei einer Figur angekommen. */
        fun onNpcReached(npc: Npc) {}

        /** Leere Kachel angetippt (nur Laufen). */
        fun onWalkOnly() {}

        /** Ziel ist unerreichbar (kein Pfad). */
        fun onBlockedTarget() {}

        /** Kamera-Schwenkmodus betreten/verlassen (für den „Zentrieren"-Chip). */
        fun onPanModeChanged(panning: Boolean) {}
    }

    var listener: Listener? = null

    val camera = Camera()

    /** Aussehen der Spielfigur (aus dem Vokabular; später aus der Story). */
    var playerLook = Look()

    /** Nachschlagefunktion für Figuren des Raums (aus dem WorldState). */
    var npcProvider: (String) -> Npc? = { null }

    private var room: Room? = null

    // Spielerposition in kontinuierlichen Kachel-Koordinaten (Mitte = i+0.5).
    private var px = 0.5f
    private var py = 0.5f

    private val path = ArrayDeque<Pair<Int, Int>>()
    private var pendingProp: Prop? = null
    private var pendingDoor: Door? = null
    private var pendingNpc: Npc? = null
    private var facingRight = true
    private var walkPhase = 0f
    private var lastFrame = 0L
    private val startTime = System.currentTimeMillis()

    private val walkSpeed = 3.2f // Kacheln pro Sekunde

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
                room?.let { camera.clampToRoom(it.w, it.h) }
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
            if (value) postInvalidateOnAnimation()
        }

    /** Bindet einen Raum und setzt den Spieler ohne Animation auf [startTile]. */
    fun bind(room: Room, startTile: Pair<Int, Int>) {
        this.room = room
        px = startTile.first + 0.5f
        py = startTile.second + 0.5f
        path.clear()
        pendingProp = null
        pendingDoor = null
        camera.jumpTo(IsoMath.worldX(px, py), IsoMath.worldY(px, py))
        camera.clampToRoom(room.w, room.h)
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

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        camera.viewW = w.toFloat()
        camera.viewH = h.toFloat()
        // Basiszoom = Displaydichte; ein bereits gepinchter Faktor bleibt erhalten.
        val relative = if (camera.baseZoom > 0f) camera.zoom / camera.baseZoom else 1f
        camera.baseZoom = resources.displayMetrics.density
        camera.zoom = camera.baseZoom * relative.coerceIn(Camera.MIN_SCALE, Camera.MAX_SCALE)
        room?.let { camera.clampToRoom(it.w, it.h) }
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
                    room?.let { camera.clampToRoom(it.w, it.h) }
                    postInvalidateOnAnimation()
                }
                lastX = event.x; lastY = event.y
            }
            MotionEvent.ACTION_UP -> {
                if (!panning && !multiTouch) handleTap(event.x, event.y)
            }
        }
        return true
    }

    private fun handleTap(sx: Float, sy: Float) {
        val r = room ?: return
        val wx = camera.worldXFromScreen(sx)
        val wy = camera.worldYFromScreen(sy)

        // Tippen beendet den Schwenkmodus — Kamera folgt wieder.
        camera.recenter()
        listener?.onPanModeChanged(false)

        // Figuren zuerst (stehen „vor" gleichrangigen Requisiten). Bei mehreren
        // Treffern gewinnt nicht das vorderste, sondern das dem Tipp-Punkt
        // nächstgelegene — sonst schluckt eine vordere Requisite mit ihrer
        // hohen Trefferbox Taps auf Dinge/Kacheln dahinter.
        val npc = r.npcIds.mapNotNull { npcProvider(it) }
            .filter { hitsTile(it.tx, it.ty, wx, wy) }
            .minByOrNull { hitDistance(it.tx, it.ty, wx, wy) }
        if (npc != null) {
            approachNpc(r, npc)
            return
        }

        val prop = r.props
            .filter { hitsTile(it.tx, it.ty, wx, wy) }
            .minByOrNull { hitDistance(it.tx, it.ty, wx, wy) }
        if (prop != null) {
            approachProp(r, prop)
            return
        }

        val tile = IsoMath.pickTile(wx, wy, r.w, r.h) ?: return
        val door = r.doorAt(tile.first, tile.second)
        if (door != null) {
            walkTo(r, tile, ontoDoor = door)
            return
        }
        walkTo(r, tile, ontoDoor = null)
        listener?.onWalkOnly()
    }

    /** Trefferbox einer Entität auf Kachel (tx,ty) in Weltkoordinaten (inkl. Höhe). */
    private fun hitsTile(tx: Int, ty: Int, wx: Float, wy: Float): Boolean {
        val cx = IsoMath.tileCenterX(tx, ty)
        val cy = IsoMath.tileCenterY(tx, ty)
        return abs(wx - cx) <= IsoMath.TILE_W * 0.35f &&
            wy >= cy - IsoMath.TILE_H * HIT_HEIGHT && wy <= cy + IsoMath.TILE_H * 0.45f
    }

    /** Abstand des Tipp-Punkts zur sichtbaren Sprite-Mitte einer Entität. */
    private fun hitDistance(tx: Int, ty: Int, wx: Float, wy: Float): Float {
        val cx = IsoMath.tileCenterX(tx, ty)
        val cy = IsoMath.tileCenterY(tx, ty) - IsoMath.TILE_H * 0.5f
        return hypot(wx - cx, wy - cy)
    }

    private fun approachNpc(r: Room, npc: Npc) {
        pendingDoor = null
        pendingProp = null
        val adj = Pathfinder.findPathAdjacent(r.w, r.h, r::walkable, playerTile(), npc.tx to npc.ty)
        if (adj == null) {
            listener?.onBlockedTarget()
            return
        }
        pendingNpc = npc
        walkPath(r, adj, immediate = adj.isEmpty())
    }

    private fun approachProp(r: Room, prop: Prop) {
        pendingDoor = null
        pendingNpc = null
        if (!prop.blocksTile) {
            // Begehbare Requisite (z. B. Teppich): direkt auf die Kachel laufen.
            pendingProp = prop
            walkPath(r, Pathfinder.findPath(r.w, r.h, r::walkable, playerTile(), prop.tx to prop.ty),
                immediate = playerTile() == prop.tx to prop.ty)
            return
        }
        val adj = Pathfinder.findPathAdjacent(r.w, r.h, r::walkable, playerTile(), prop.tx to prop.ty)
        if (adj == null) {
            listener?.onBlockedTarget()
            return
        }
        pendingProp = prop
        walkPath(r, adj, immediate = adj.isEmpty())
    }

    private fun walkTo(r: Room, tile: Pair<Int, Int>, ontoDoor: Door?) {
        pendingProp = null
        pendingNpc = null
        pendingDoor = ontoDoor
        if (tile == playerTile()) {
            arrive()
            return
        }
        val p = Pathfinder.findPath(r.w, r.h, r::walkable, playerTile(), tile)
        if (p.isEmpty()) {
            // Blockierte/unerreichbare Kachel: so nah wie möglich heranlaufen,
            // statt sofort „Da kommst du nicht hin" zu melden.
            pendingDoor = null
            val adj = Pathfinder.findPathAdjacent(r.w, r.h, r::walkable, playerTile(), tile)
            if (adj == null) {
                listener?.onBlockedTarget()
                return
            }
            if (adj.isEmpty()) return // steht bereits direkt daneben
            walkPath(r, adj, immediate = false)
            return
        }
        walkPath(r, p, immediate = false)
    }

    private fun walkPath(r: Room, newPath: List<Pair<Int, Int>>, immediate: Boolean) {
        path.clear()
        if (immediate) {
            arrive()
            return
        }
        if (newPath.isEmpty()) {
            pendingProp = null
            pendingDoor = null
            listener?.onBlockedTarget()
            return
        }
        path.addAll(newPath)
        lastFrame = System.currentTimeMillis()
        postInvalidateOnAnimation()
    }

    private fun arrive() {
        pendingProp?.let { p ->
            pendingProp = null
            listener?.onPropReached(p)
        }
        pendingNpc?.let { n ->
            pendingNpc = null
            listener?.onNpcReached(n)
        }
        pendingDoor?.let { d ->
            pendingDoor = null
            listener?.onDoorReached(d)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val r = room ?: return
        val now = System.currentTimeMillis()
        val timeMs = now - startTime

        // Hintergrund im Bildschirmraum
        PaintKit.verticalGradient(
            canvas, 0f, 0f, width.toFloat(), height.toFloat(),
            r.palette.bgTop, r.palette.bgBottom,
        )

        // Bewegung fortschreiben
        val dt = if (lastFrame == 0L) 0f else (now - lastFrame).coerceAtMost(64L) / 1000f
        lastFrame = now
        stepMovement(dt)

        // Kamera folgt dem Spieler
        camera.follow(IsoMath.worldX(px, py), IsoMath.worldY(px, py), dt)
        camera.clampToRoom(r.w, r.h)

        canvas.save()
        canvas.translate(camera.viewW / 2f, camera.viewH / 2f)
        canvas.scale(camera.zoom, camera.zoom)
        canvas.translate(-camera.x, -camera.y)

        RoomRenderer.drawFloor(canvas, r)
        RoomRenderer.drawDoorMarkers(canvas, r)
        RoomRenderer.drawWalls(canvas, r, timeMs)

        // Entities in Painter-Reihenfolge (Requisiten + Figuren + Spieler)
        val walking = path.isNotEmpty()
        val entities = ArrayList<Pair<Float, () -> Unit>>(r.props.size + r.npcIds.size + 1)
        r.props.forEach { p ->
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
        val labelScale = if (camera.zoom > 0f && camera.baseZoom > 0f) {
            (camera.baseZoom / camera.zoom).coerceAtLeast(1f)
        } else 1f
        RoomRenderer.drawDoorLabels(canvas, r, timeMs, labelScale)

        canvas.restore()

        if (animate) postInvalidateOnAnimation()
    }

    private companion object {
        /** Vertikale Ausdehnung der Trefferbox in Kachelhöhen (Sprites sind hoch). */
        const val HIT_HEIGHT = 1.15f
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
