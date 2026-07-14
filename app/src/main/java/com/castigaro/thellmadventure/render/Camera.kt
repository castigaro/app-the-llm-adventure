package com.castigaro.thellmadventure.render

import kotlin.math.min

/**
 * Die Kamera macht Räume größer als das Display möglich — das gab es bei
 * Kap-Krakenzahn nicht (dort war ein Raum immer exakt bildschirmgroß).
 *
 * [x]/[y] ist der Weltpunkt, der in der Viewport-Mitte liegt. Standard ist
 * Follow-Modus (weiches Nachziehen auf den Spieler); ein Ein-Finger-Drag
 * schaltet auf manuelles Schwenken um, bis [recenter] gerufen wird.
 */
class Camera {

    var x = 0f
        private set
    var y = 0f
        private set

    /** Zoomfaktor Welt→Bildschirm (wird von der View auf die Displaydichte gesetzt). */
    var zoom = 1f

    var viewW = 0f
    var viewH = 0f

    /** true, solange der Spieler manuell geschwenkt hat (Follow pausiert). */
    var manualPan = false
        private set

    /** Setzt die Kamera hart auf einen Weltpunkt (z. B. nach Raumwechsel). */
    fun jumpTo(wx: Float, wy: Float) {
        x = wx
        y = wy
        manualPan = false
    }

    /** Weiches Nachziehen Richtung Spieler; im Pan-Modus passiert nichts. */
    fun follow(targetWx: Float, targetWy: Float, dtSeconds: Float) {
        if (manualPan) return
        val t = min(1f, dtSeconds * 4f)
        x += (targetWx - x) * t
        y += (targetWy - y) * t
    }

    /** Manuelles Schwenken um einen Bildschirm-Delta (Finger-Drag). */
    fun panBy(dxPx: Float, dyPx: Float) {
        manualPan = true
        x -= dxPx / zoom
        y -= dyPx / zoom
    }

    /** Follow-Modus wieder aktivieren (Tipp zum Laufen oder „Zentrieren"-Chip). */
    fun recenter() {
        manualPan = false
    }

    /**
     * Klemmt die Kamera an die Raumgrenzen: Räume kleiner als der Viewport
     * werden zentriert, größere scrollen ohne über den Rand hinauszulaufen.
     */
    fun clampToRoom(roomW: Int, roomH: Int) {
        if (viewW <= 0f || viewH <= 0f || zoom <= 0f) return
        val margin = IsoMath.TILE_H
        val minX = IsoMath.roomMinX(roomH) - margin
        val maxX = IsoMath.roomMaxX(roomW) + margin
        val minY = IsoMath.roomMinY() - margin
        val maxY = IsoMath.roomMaxY(roomW, roomH) + margin
        x = clampAxis(x, minX, maxX, viewW / (2f * zoom))
        y = clampAxis(y, minY, maxY, viewH / (2f * zoom))
    }

    private fun clampAxis(value: Float, min: Float, max: Float, halfView: Float): Float {
        // Passt der Raum komplett in den Viewport, wird zentriert.
        if (max - min <= 2f * halfView) return (min + max) / 2f
        return value.coerceIn(min + halfView, max - halfView)
    }

    /** Bildschirm→Welt (für Touch-Picking). */
    fun worldXFromScreen(sx: Float): Float = (sx - viewW / 2f) / zoom + x

    fun worldYFromScreen(sy: Float): Float = (sy - viewH / 2f) / zoom + y
}
