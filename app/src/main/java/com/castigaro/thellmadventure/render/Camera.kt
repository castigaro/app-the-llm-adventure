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

    /** Effektiver Zoomfaktor Welt→Bildschirm (Basiszoom × Nutzer-Pinch). */
    var zoom = 1f

    /** Basiszoom = Displaydichte; der Pinch-Faktor bewegt sich relativ dazu. */
    var baseZoom = 0f

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
     * Pinch-Zoom um einen Bildschirm-Fokuspunkt: Der Weltpunkt unter den
     * Fingern bleibt stehen. Geklemmt auf [MIN_SCALE]..[MAX_SCALE] relativ
     * zum Basiszoom.
     */
    fun scaleBy(factor: Float, focusSx: Float, focusSy: Float) {
        if (baseZoom <= 0f) return
        val newZoom = (zoom * factor).coerceIn(baseZoom * MIN_SCALE, baseZoom * MAX_SCALE)
        if (newZoom == zoom) return
        val wx = worldXFromScreen(focusSx)
        val wy = worldYFromScreen(focusSy)
        zoom = newZoom
        x = wx - (focusSx - viewW / 2f) / zoom
        y = wy - (focusSy - viewH / 2f) / zoom
        manualPan = true
    }

    /**
     * Klemmt die Kamera an die Raumgrenzen: Räume kleiner als der Viewport
     * werden zentriert, größere scrollen ohne über den Rand hinauszulaufen.
     */
    fun clampToRoom(roomW: Int, roomH: Int) {
        clampToBounds(
            IsoMath.roomMinX(roomH), IsoMath.roomMaxX(roomW),
            IsoMath.roomMinY(), IsoMath.roomMaxY(roomW, roomH),
        )
    }

    /** Klemmt die Kamera an beliebige Weltgrenzen (Außenkarte, Räume). */
    fun clampToBounds(minWx: Float, maxWx: Float, minWy: Float, maxWy: Float) {
        if (viewW <= 0f || viewH <= 0f || zoom <= 0f) return
        val margin = IsoMath.TILE_H
        x = clampAxis(x, minWx - margin, maxWx + margin, viewW / (2f * zoom))
        y = clampAxis(y, minWy - margin, maxWy + margin, viewH / (2f * zoom))
    }

    private fun clampAxis(value: Float, min: Float, max: Float, halfView: Float): Float {
        // Passt der Raum komplett in den Viewport, wird zentriert.
        if (max - min <= 2f * halfView) return (min + max) / 2f
        return value.coerceIn(min + halfView, max - halfView)
    }

    /** Bildschirm→Welt (für Touch-Picking). */
    fun worldXFromScreen(sx: Float): Float = (sx - viewW / 2f) / zoom + x

    fun worldYFromScreen(sy: Float): Float = (sy - viewH / 2f) / zoom + y

    companion object {
        const val MIN_SCALE = 0.6f
        const val MAX_SCALE = 2.5f
    }
}
