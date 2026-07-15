package com.castigaro.thellmadventure.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Gemeinsame Zeichenwerkzeuge für den flachen Vektor-Stil (Muster aus
 * Kap-Krakenzahn). Alles wird programmatisch gemalt — keine Bild-Assets,
 * damit die KI Inhalte als reine DATEN erschaffen kann.
 */
object PaintKit {

    const val WEISS = 0xFFF5F1E6.toInt()
    const val GOLD = 0xFFE0A73C.toInt()
    const val SCHATTEN = 0x33000000

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = WEISS
        textAlign = Paint.Align.CENTER
    }

    // Paint-Caches: Die Maler fordern pro Frame hunderte Paints an — ohne
    // Cache wären das ebenso viele Allokationen. WICHTIG: Zurückgegebene
    // Paints dürfen von Aufrufern NICHT verändert werden (geteilte Instanzen);
    // veränderlich ist nur [text]. Zeichnen läuft ausschließlich auf dem
    // UI-Thread, darum reichen einfache HashMaps.
    private val solidCache = HashMap<Int, Paint>()
    private val lineCache = HashMap<Long, Paint>()

    fun solid(color: Int): Paint = solidCache.getOrPut(color) {
        Paint(fill).apply { this.color = color }
    }

    fun line(color: Int, width: Float): Paint {
        val key = (color.toLong() shl 32) or (width.toRawBits().toLong() and 0xFFFFFFFFL)
        return lineCache.getOrPut(key) {
            Paint(stroke).apply {
                this.color = color
                this.strokeWidth = width
            }
        }
    }

    /** Hellt (factor > 1) oder dunkelt (factor < 1) eine Farbe kanalweise ab. */
    fun shade(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), r, g, b)
    }

    fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

    /**
     * Hellt additiv auf (Lichtkanten, Glanzpunkte) — wirkt im Gegensatz zu
     * [shade] auch bei sehr dunklen Farben. (Muster aus Kap-Krakenzahn.)
     */
    fun lighten(color: Int, amount: Int = 40): Int = Color.argb(
        Color.alpha(color),
        (Color.red(color) + amount).coerceIn(0, 255),
        (Color.green(color) + amount).coerceIn(0, 255),
        (Color.blue(color) + amount).coerceIn(0, 255),
    )

    /** Weicher radialer Schein (Fackeln, Kristalle, magische Gegenstände). */
    fun radialGlow(canvas: Canvas, cx: Float, cy: Float, radius: Float, centerColor: Int) {
        val edge = centerColor and 0x00FFFFFF // gleiche Farbe, Alpha 0
        val p = Paint().apply {
            shader = RadialGradient(cx, cy, radius, centerColor, edge, Shader.TileMode.CLAMP)
        }
        canvas.drawCircle(cx, cy, radius, p)
    }

    /** Vertikaler Farbverlauf über ein Rechteck (Hintergrund). */
    fun verticalGradient(
        canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float,
        colorTop: Int, colorBottom: Int,
    ) {
        val p = Paint()
        p.shader = LinearGradient(0f, top, 0f, bottom, colorTop, colorBottom, Shader.TileMode.CLAMP)
        canvas.drawRect(left, top, right, bottom, p)
    }

    /** Iso-Raute um einen Mittelpunkt. */
    fun diamond(cx: Float, cy: Float, halfW: Float, halfH: Float): Path = Path().apply {
        moveTo(cx, cy - halfH)
        lineTo(cx + halfW, cy)
        lineTo(cx, cy + halfH)
        lineTo(cx - halfW, cy)
        close()
    }

    /**
     * Ein Iso-Quader, der auf einer Kachelmitte steht: Deckel-Raute plus
     * linke und rechte Seitenfläche mit passender Schattierung. Das
     * Grundbaustein-Element fast aller Requisiten.
     */
    fun cube(
        canvas: Canvas, cx: Float, baseY: Float,
        halfW: Float, halfH: Float, height: Float, color: Int,
    ) {
        val topY = baseY - height
        // Linke Fläche (dunkler)
        canvas.drawPath(Path().apply {
            moveTo(cx - halfW, topY)
            lineTo(cx, topY + halfH)
            lineTo(cx, baseY + halfH)
            lineTo(cx - halfW, baseY)
            close()
        }, solid(shade(color, 0.72f)))
        // Rechte Fläche (mittel)
        canvas.drawPath(Path().apply {
            moveTo(cx + halfW, topY)
            lineTo(cx, topY + halfH)
            lineTo(cx, baseY + halfH)
            lineTo(cx + halfW, baseY)
            close()
        }, solid(shade(color, 0.88f)))
        // Deckel (hell)
        canvas.drawPath(diamond(cx, topY, halfW, halfH), solid(shade(color, 1.12f)))
        // Lichtkante an den oberen Deckelkanten — gibt jedem Quader Kontur
        val bevel = line(shade(color, 1.35f), halfH * 0.12f)
        canvas.drawLine(cx - halfW, topY, cx, topY - halfH, bevel)
        canvas.drawLine(cx, topY - halfH, cx + halfW, topY, bevel)
    }

    /**
     * Dunkle Kantenlinien um einen Iso-Quader — dieselbe Geometrie wie
     * [cube]. Hebt Objekte sichtbar vom Boden und voneinander ab.
     */
    fun cubeEdges(
        canvas: Canvas, cx: Float, baseY: Float,
        halfW: Float, halfH: Float, height: Float, color: Int, width: Float,
    ) {
        val topY = baseY - height
        val edge = line(shade(color, 0.45f), width)
        canvas.drawPath(diamond(cx, topY, halfW, halfH), edge)
        canvas.drawLine(cx - halfW, topY, cx - halfW, baseY, edge)
        canvas.drawLine(cx + halfW, topY, cx + halfW, baseY, edge)
        canvas.drawLine(cx, topY + halfH, cx, baseY + halfH, edge)
    }

    /** Stehender Zylinder (Fass, Kessel, Brunnen). */
    fun cylinder(
        canvas: Canvas, cx: Float, baseY: Float,
        radius: Float, height: Float, color: Int,
    ) {
        val topY = baseY - height
        val oval = radius * 0.5f
        canvas.drawRect(cx - radius, topY, cx + radius, baseY, solid(shade(color, 0.85f)))
        canvas.drawOval(RectF(cx - radius, baseY - oval, cx + radius, baseY + oval), solid(shade(color, 0.72f)))
        canvas.drawOval(RectF(cx - radius, topY - oval, cx + radius, topY + oval), solid(shade(color, 1.12f)))
    }

    /** Weicher Bodenschatten unter Figuren und Requisiten. */
    fun dropShadow(canvas: Canvas, cx: Float, cy: Float, halfW: Float) {
        canvas.drawOval(
            RectF(cx - halfW, cy - halfW * 0.32f, cx + halfW, cy + halfW * 0.32f),
            solid(SCHATTEN),
        )
    }

    fun roundedRect(l: Float, t: Float, r: Float, b: Float, radius: Float): Path =
        Path().apply { addRoundRect(RectF(l, t, r, b), radius, radius, Path.Direction.CW) }
}
