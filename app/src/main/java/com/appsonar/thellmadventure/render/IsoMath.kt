package com.appsonar.thellmadventure.render

import kotlin.math.floor

/**
 * Reine isometrische Mathematik (2:1-Rauten), bewusst ohne Android-Imports —
 * damit sie als schneller JVM-Unit-Test prüfbar bleibt.
 *
 * Koordinatensysteme:
 *  - Kachel (tx, ty): Spaltenachse läuft nach „rechts unten", Zeilenachse
 *    nach „links unten". Kachelmitte von Kachel (i, j) ist (i+0.5, j+0.5).
 *  - Welt (wx, wy): Pixel im ungezoomten Weltraum; Ursprung an der oberen
 *    Raumecke (Kachel 0,0).
 *  - Bildschirm: Welt minus Kameraposition, mal Zoom, plus Viewport-Mitte
 *    (macht die [Camera] bzw. die Canvas-Transformation in der IsoView).
 */
object IsoMath {

    const val TILE_W = 128f
    const val TILE_H = 64f

    /** Wandhöhe in Weltpixeln (Nord-/Westwand ragt nach oben). */
    const val WALL_H = TILE_H * 1.8f

    fun worldX(tx: Float, ty: Float): Float = (tx - ty) * (TILE_W / 2f)

    fun worldY(tx: Float, ty: Float): Float = (tx + ty) * (TILE_H / 2f)

    fun tileX(wx: Float, wy: Float): Float = (wx / (TILE_W / 2f) + wy / (TILE_H / 2f)) / 2f

    fun tileY(wx: Float, wy: Float): Float = (wy / (TILE_H / 2f) - wx / (TILE_W / 2f)) / 2f

    /** Weltposition der Mitte von Kachel (tx, ty). */
    fun tileCenterX(tx: Int, ty: Int): Float = worldX(tx + 0.5f, ty + 0.5f)

    fun tileCenterY(tx: Int, ty: Int): Float = worldY(tx + 0.5f, ty + 0.5f)

    /** Ganzzahlige Kachel unter einem Weltpunkt, oder null außerhalb des Raums. */
    fun pickTile(wx: Float, wy: Float, roomW: Int, roomH: Int): Pair<Int, Int>? {
        val tx = floor(tileX(wx, wy)).toInt()
        val ty = floor(tileY(wx, wy)).toInt()
        return if (tx in 0 until roomW && ty in 0 until roomH) tx to ty else null
    }

    /** Ganzzahlige Kachel unter einem Weltpunkt — ohne Grenzen (Außenkarte). */
    fun pickTileUnbounded(wx: Float, wy: Float): Pair<Int, Int> =
        floor(tileX(wx, wy)).toInt() to floor(tileY(wx, wy)).toInt()

    /**
     * Painter's Algorithm: Entities mit kleinerer Kachelsumme liegen „hinten"
     * und werden zuerst gezeichnet; bei Gleichstand gewinnt das kleinere ty.
     * Negativ = a vor b zeichnen.
     */
    fun drawOrder(aTx: Int, aTy: Int, bTx: Int, bTy: Int): Int {
        val sum = (aTx + aTy).compareTo(bTx + bTy)
        return if (sum != 0) sum else aTy.compareTo(bTy)
    }

    // Raumgrenzen in Weltkoordinaten (Bounding-Box aller vier Bodenecken).

    fun roomMinX(roomH: Int): Float = worldX(0f, roomH.toFloat())

    fun roomMaxX(roomW: Int): Float = worldX(roomW.toFloat(), 0f)

    /** Oberkante inklusive Wandhöhe. */
    fun roomMinY(): Float = -WALL_H

    fun roomMaxY(roomW: Int, roomH: Int): Float = worldY(roomW.toFloat(), roomH.toFloat())

    // Grenzen eines beliebigen Kachelbereichs [minTx..maxTxExkl) × [minTy..maxTyExkl)
    // in Weltkoordinaten — die Extreme liegen an den vier Eck-Kacheln der Raute.

    fun boundsMinX(minTx: Int, maxTyExkl: Int): Float = worldX(minTx.toFloat(), maxTyExkl.toFloat())

    fun boundsMaxX(maxTxExkl: Int, minTy: Int): Float = worldX(maxTxExkl.toFloat(), minTy.toFloat())

    fun boundsMinY(minTx: Int, minTy: Int): Float = worldY(minTx.toFloat(), minTy.toFloat())

    fun boundsMaxY(maxTxExkl: Int, maxTyExkl: Int): Float = worldY(maxTxExkl.toFloat(), maxTyExkl.toFloat())

    /**
     * Kachelbereich, der ein Welt-Rechteck (Kamera-Ausschnitt) vollständig
     * umschließt: [minTx, minTy, maxTxExkl, maxTyExkl]. Grundlage des
     * Frustum-Cullings — nur diese Kacheln werden gezeichnet. [padTiles]
     * gibt Rand für hohe Sprites/Gebäude, die in den Ausschnitt hineinragen.
     */
    fun visibleTileRange(
        minWx: Float, minWy: Float, maxWx: Float, maxWy: Float,
        padTiles: Int = 3,
    ): IntArray {
        val ecken = arrayOf(
            tileX(minWx, minWy) to tileY(minWx, minWy),
            tileX(maxWx, minWy) to tileY(maxWx, minWy),
            tileX(minWx, maxWy) to tileY(minWx, maxWy),
            tileX(maxWx, maxWy) to tileY(maxWx, maxWy),
        )
        val minTx = floor(ecken.minOf { it.first }).toInt() - padTiles
        val maxTx = floor(ecken.maxOf { it.first }).toInt() + 1 + padTiles
        val minTy = floor(ecken.minOf { it.second }).toInt() - padTiles
        val maxTy = floor(ecken.maxOf { it.second }).toInt() + 1 + padTiles
        return intArrayOf(minTx, minTy, maxTx, maxTy)
    }
}
