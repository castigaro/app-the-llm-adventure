package com.castigaro.thellmadventure.render

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
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

    fun solid(color: Int): Paint = Paint(fill).apply { this.color = color }

    fun line(color: Int, width: Float): Paint = Paint(stroke).apply {
        this.color = color
        this.strokeWidth = width
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
