package com.castigaro.thellmadventure.render

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.castigaro.thellmadventure.world.Terrain
import kotlin.math.sin

/**
 * Malt einzelne Geländekacheln mit Detail: Grashalme, Blumen, Steinsprenkel,
 * Wellenglanz, Risse — alles deterministisch aus [hash] der Kachelkoordinate
 * abgeleitet (kein Zufall pro Frame, das Bild steht still). Übergänge werden
 * über Kantensäume gemalt: Grenzt Weg/Sand/Erde an Gras, wächst ein grüner
 * Saum in die Kachel — das macht den „Paperboy"-Look statt Schachbrett.
 *
 * Alles außer den Wasser-Glanzbändern ([drawWasserGlanz]) ist statisch und
 * wird von der IsoView in ein Picture je Parzelle aufgenommen.
 */
object TerrainPainter {

    private val TW = IsoMath.TILE_W
    private val TH = IsoMath.TILE_H

    private const val GRAS_SAUM = 0xFF3E8E4A.toInt()
    private const val SCHAUM = 0xFFDCEBF2.toInt()
    private val BLUETEN = intArrayOf(0xFFE0A73C.toInt(), 0xFFD86A8A.toInt(), 0xFFF0E0B8.toInt(), 0xFF7FC7E8.toInt())

    /** Deterministischer, positiver Streuwert je Kachel (öffentlich für Tests). */
    fun hash(tx: Int, ty: Int): Int {
        var h = tx * 73856093 xor ty * 19349663
        h = h xor (h shr 13)
        return h and 0x7FFFFFFF
    }

    /**
     * Statischer Teil einer Kachel: Grundraute, Detail, Kantensäume, Schaum.
     * [nachbar] liefert das Gelände der vier Nachbarkacheln (null = Nebel).
     */
    fun drawStatic(
        canvas: Canvas,
        terrain: Terrain,
        tx: Int,
        ty: Int,
        nachbar: (Int, Int) -> Terrain?,
    ) {
        val cx = IsoMath.tileCenterX(tx, ty)
        val cy = IsoMath.tileCenterY(tx, ty)
        val base = if (Math.floorMod(tx + ty, 2) == 0) terrain.farbeA else terrain.farbeB
        canvas.drawPath(PaintKit.diamond(cx, cy, TW / 2f, TH / 2f), PaintKit.solid(base))

        val h = hash(tx, ty)
        when (terrain) {
            Terrain.GRAS -> {
                halme(canvas, cx, cy, base, h, anzahl = 3)
                if (h % 13 == 0) bluete(canvas, cx, cy, h)
            }
            Terrain.BLUMEN -> {
                halme(canvas, cx, cy, base, h, anzahl = 2)
                for (i in 0..2) bluete(canvas, cx, cy, h + i * 7919)
            }
            Terrain.WEG -> sprenkel(canvas, cx, cy, base, h, anzahl = 3)
            Terrain.SAND -> {
                sprenkel(canvas, cx, cy, base, h, anzahl = 2)
                // Windrippel
                val rippel = PaintKit.line(PaintKit.shade(base, 0.9f), TH * 0.03f)
                val ry = cy + versatz(h, 3, TH * 0.2f)
                canvas.drawLine(cx - TW * 0.18f, ry, cx + TW * 0.14f, ry - TH * 0.06f, rippel)
            }
            Terrain.ERDE -> {
                // Trockenrisse
                val riss = PaintKit.line(PaintKit.shade(base, 0.75f), TH * 0.03f)
                val rx = cx + versatz(h, 1, TW * 0.12f)
                canvas.drawLine(rx - TW * 0.10f, cy - TH * 0.06f, rx + TW * 0.04f, cy + TH * 0.08f, riss)
                canvas.drawLine(rx + TW * 0.04f, cy + TH * 0.08f, rx + TW * 0.12f, cy + TH * 0.02f, riss)
            }
            Terrain.FELS -> findling(canvas, cx, cy, base, h)
            Terrain.WASSER -> {} // Glanz kommt animiert, Schaum unten
        }

        // Kantensäume: Gras wächst in begehbare Nachbarn hinein.
        if (terrain != Terrain.GRAS && terrain != Terrain.BLUMEN && terrain.begehbar) {
            kantensaum(canvas, cx, cy, tx, ty, nachbar) { it == Terrain.GRAS || it == Terrain.BLUMEN }
        }
        // Schaumkante: Wasser an begehbarem Ufer.
        if (terrain == Terrain.WASSER) {
            schaumkante(canvas, cx, cy, tx, ty, nachbar)
        }
    }

    /** Animierte Glanzbänder einer Wasserkachel (bleibt außerhalb des Pictures). */
    fun drawWasserGlanz(canvas: Canvas, tx: Int, ty: Int, timeMs: Long) {
        val cx = IsoMath.tileCenterX(tx, ty)
        val cy = IsoMath.tileCenterY(tx, ty)
        val h = hash(tx, ty)
        for (i in 0..1) {
            val schwung = sin(timeMs / 650.0 + h % 7 + i * 2.1).toFloat()
            val y = cy + schwung * TH * 0.12f + (i - 0.5f) * TH * 0.16f
            val halbe = TW * (0.30f - 0.10f * i)
            canvas.drawLine(
                cx - halbe, y, cx + halbe, y,
                PaintKit.line(PaintKit.withAlpha(PaintKit.WEISS, 46), TH * 0.05f),
            )
        }
    }

    // ---- Detail-Bausteine ------------------------------------------------------

    private fun halme(canvas: Canvas, cx: Float, cy: Float, base: Int, h: Int, anzahl: Int) {
        val halm = PaintKit.line(PaintKit.shade(base, 1.22f), TH * 0.035f)
        for (i in 0 until anzahl) {
            val dx = versatz(h, i * 2, TW * 0.26f)
            val dy = versatz(h, i * 2 + 1, TH * 0.22f)
            canvas.drawLine(cx + dx, cy + dy, cx + dx + TW * 0.02f, cy + dy - TH * 0.14f, halm)
        }
    }

    private fun bluete(canvas: Canvas, cx: Float, cy: Float, h: Int) {
        val farbe = BLUETEN[Math.floorMod(h / 31, BLUETEN.size)]
        val bx = cx + versatz(h, 5, TW * 0.24f)
        val by = cy + versatz(h, 6, TH * 0.22f)
        canvas.drawCircle(bx, by, TH * 0.055f, PaintKit.solid(farbe))
        canvas.drawCircle(bx, by, TH * 0.022f, PaintKit.solid(PaintKit.shade(farbe, 0.6f)))
    }

    private fun sprenkel(canvas: Canvas, cx: Float, cy: Float, base: Int, h: Int, anzahl: Int) {
        for (i in 0 until anzahl) {
            val dx = versatz(h, i * 2, TW * 0.28f)
            val dy = versatz(h, i * 2 + 1, TH * 0.24f)
            val ton = if (i % 2 == 0) PaintKit.shade(base, 1.14f) else PaintKit.shade(base, 0.82f)
            canvas.drawOval(
                RectF(cx + dx - TH * 0.05f, cy + dy - TH * 0.03f, cx + dx + TH * 0.05f, cy + dy + TH * 0.03f),
                PaintKit.solid(ton),
            )
        }
    }

    private fun findling(canvas: Canvas, cx: Float, cy: Float, base: Int, h: Int) {
        val dx = versatz(h, 2, TW * 0.10f)
        val brocken = Path().apply {
            moveTo(cx + dx - TW * 0.16f, cy + TH * 0.10f)
            lineTo(cx + dx - TW * 0.10f, cy - TH * 0.22f)
            lineTo(cx + dx + TW * 0.08f, cy - TH * 0.26f)
            lineTo(cx + dx + TW * 0.16f, cy + TH * 0.08f)
            close()
        }
        canvas.drawPath(brocken, PaintKit.solid(PaintKit.shade(base, 1.18f)))
        val schatten = Path().apply {
            moveTo(cx + dx + TW * 0.08f, cy - TH * 0.26f)
            lineTo(cx + dx + TW * 0.16f, cy + TH * 0.08f)
            lineTo(cx + dx + TW * 0.02f, cy + TH * 0.10f)
            close()
        }
        canvas.drawPath(schatten, PaintKit.solid(PaintKit.shade(base, 0.8f)))
    }

    /**
     * Saum entlang der Kanten zu Nachbarn, die [passt] erfüllen: ein flaches
     * Band von der Kante ein Stück in die Kachel hinein.
     */
    private inline fun kantensaum(
        canvas: Canvas, cx: Float, cy: Float, tx: Int, ty: Int,
        nachbar: (Int, Int) -> Terrain?,
        passt: (Terrain) -> Boolean,
    ) {
        val farbe = PaintKit.withAlpha(GRAS_SAUM, 150)
        kanten(tx, ty).forEach { kante ->
            val n = nachbar(kante.ntx, kante.nty) ?: return@forEach
            if (!passt(n)) return@forEach
            canvas.drawPath(saumPfad(cx, cy, kante), PaintKit.solid(farbe))
        }
    }

    private fun schaumkante(canvas: Canvas, cx: Float, cy: Float, tx: Int, ty: Int, nachbar: (Int, Int) -> Terrain?) {
        val stift = PaintKit.line(PaintKit.withAlpha(SCHAUM, 130), TH * 0.05f)
        kanten(tx, ty).forEach { kante ->
            val n = nachbar(kante.ntx, kante.nty) ?: return@forEach
            if (!n.begehbar) return@forEach
            canvas.drawLine(kante.ax(cx, cy), kante.ay(cx, cy), kante.bx(cx, cy), kante.by(cx, cy), stift)
        }
    }

    /** Eine Rautenkante samt zugehörigem Nachbarn. Ecken: O(ben) R L U(nten). */
    private class Kante(
        val ntx: Int, val nty: Int,
        val axF: Float, val ayF: Float, val bxF: Float, val byF: Float,
    ) {
        fun ax(cx: Float, cy: Float) = cx + axF * TW
        fun ay(cx: Float, cy: Float) = cy + ayF * TH
        fun bx(cx: Float, cy: Float) = cx + bxF * TW
        fun by(cx: Float, cy: Float) = cy + byF * TH
    }

    private fun kanten(tx: Int, ty: Int): List<Kante> = listOf(
        // Nachbar (tx, ty-1): Kante obere Ecke → rechte Ecke
        Kante(tx, ty - 1, 0f, -0.5f, 0.5f, 0f),
        // Nachbar (tx+1, ty): rechte Ecke → untere Ecke
        Kante(tx + 1, ty, 0.5f, 0f, 0f, 0.5f),
        // Nachbar (tx, ty+1): untere Ecke → linke Ecke
        Kante(tx, ty + 1, 0f, 0.5f, -0.5f, 0f),
        // Nachbar (tx-1, ty): linke Ecke → obere Ecke
        Kante(tx - 1, ty, -0.5f, 0f, 0f, -0.5f),
    )

    /** Saum: Kante plus die beiden zu 28 % zur Mitte gezogenen Eckpunkte. */
    private fun saumPfad(cx: Float, cy: Float, kante: Kante): Path {
        val t = 0.28f
        val ax = kante.ax(cx, cy); val ay = kante.ay(cx, cy)
        val bx = kante.bx(cx, cy); val by = kante.by(cx, cy)
        return Path().apply {
            moveTo(ax, ay)
            lineTo(bx, by)
            lineTo(bx + (cx - bx) * t, by + (cy - by) * t)
            lineTo(ax + (cx - ax) * t, ay + (cy - ay) * t)
            close()
        }
    }

    /** Deterministischer Versatz in [-spanne, +spanne] aus Hash-Bits [schritt]. */
    private fun versatz(h: Int, schritt: Int, spanne: Float): Float {
        val bits = (h shr (schritt * 5)) and 0x3F // 0..63
        return (bits - 32) / 32f * spanne
    }
}
