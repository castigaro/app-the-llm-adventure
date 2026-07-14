package com.castigaro.thellmadventure.render

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.castigaro.thellmadventure.world.IconShape
import com.castigaro.thellmadventure.world.IconSpec
import com.castigaro.thellmadventure.world.SLOT_COLORS

/**
 * Prozedurale Icons für Gegenstände und Fähigkeiten: farbige Kachel +
 * Form-Silhouette + optionale Glyphe. Ersetzt Kap-Krakenzahns unübersicht-
 * liche 2-Buchstaben-Quadrate — die Form trägt die Wiedererkennung, der
 * Name steht im ausgeklappten Panel daneben.
 */
object IconPainter {

    /** Malt das Icon in das Quadrat (0,0)–(size,size) der Canvas. */
    fun draw(canvas: Canvas, spec: IconSpec, size: Float, selected: Boolean = false) {
        val color = SLOT_COLORS[spec.colorIdx.coerceIn(0, SLOT_COLORS.size - 1)]
        val pad = size * 0.06f
        val radius = size * 0.18f

        // Kachel mit Oberlicht (Lighten-Trick aus Kap-Krakenzahn)
        canvas.drawPath(PaintKit.roundedRect(pad, pad, size - pad, size - pad, radius), PaintKit.solid(color))
        canvas.drawPath(
            PaintKit.roundedRect(pad, pad, size - pad, size * 0.45f, radius),
            PaintKit.solid(PaintKit.withAlpha(PaintKit.WEISS, 42)),
        )
        if (selected) {
            canvas.drawPath(
                PaintKit.roundedRect(pad, pad, size - pad, size - pad, radius),
                PaintKit.line(PaintKit.GOLD, size * 0.06f),
            )
        }

        // Form-Silhouette in aufgehellter Tönung
        val fg = PaintKit.solid(PaintKit.shade(color, 1.6f))
        val c = size / 2f
        val r = size * 0.26f
        when (spec.shape) {
            IconShape.KREIS -> canvas.drawCircle(c, c, r, fg)
            IconShape.RAUTE -> canvas.drawPath(PaintKit.diamond(c, c, r, r * 1.15f), fg)
            IconShape.FLASCHE -> {
                canvas.drawRect(c - r * 0.25f, c - r * 1.2f, c + r * 0.25f, c - r * 0.5f, fg)
                canvas.drawPath(PaintKit.roundedRect(c - r * 0.6f, c - r * 0.5f, c + r * 0.6f, c + r * 1.1f, r * 0.4f), fg)
            }
            IconShape.SCHLUESSEL -> {
                canvas.drawCircle(c - r * 0.55f, c - r * 0.45f, r * 0.5f, fg)
                canvas.drawRect(c - r * 0.35f, c - r * 0.30f, c + r * 1.0f, c - r * 0.02f, fg)
                canvas.drawRect(c + r * 0.45f, c - r * 0.02f, c + r * 0.65f, c + r * 0.45f, fg)
                canvas.drawRect(c + r * 0.80f, c - r * 0.02f, c + r * 1.0f, c + r * 0.55f, fg)
            }
            IconShape.ZAHNRAD -> {
                canvas.drawCircle(c, c, r * 0.75f, fg)
                for (i in 0 until 8) {
                    val angle = Math.toRadians(i * 45.0)
                    val zx = c + (r * 0.95f) * Math.cos(angle).toFloat()
                    val zy = c + (r * 0.95f) * Math.sin(angle).toFloat()
                    canvas.drawCircle(zx, zy, r * 0.2f, fg)
                }
                canvas.drawCircle(c, c, r * 0.3f, PaintKit.solid(color))
            }
            IconShape.ROLLE -> {
                canvas.drawPath(PaintKit.roundedRect(c - r, c - r * 0.7f, c + r, c + r * 0.7f, r * 0.35f), fg)
                canvas.drawCircle(c - r, c, r * 0.35f, fg)
                canvas.drawCircle(c + r, c, r * 0.35f, fg)
            }
            IconShape.STERN -> canvas.drawPath(star(c, c, r), fg)
            IconShape.TROPFEN -> {
                canvas.drawPath(Path().apply {
                    moveTo(c, c - r * 1.1f)
                    quadTo(c + r, c + r * 0.1f, c, c + r)
                    quadTo(c - r, c + r * 0.1f, c, c - r * 1.1f)
                    close()
                }, fg)
            }
            IconShape.SCHILD -> {
                canvas.drawPath(Path().apply {
                    moveTo(c - r, c - r * 0.9f)
                    lineTo(c + r, c - r * 0.9f)
                    lineTo(c + r, c + r * 0.2f)
                    quadTo(c, c + r * 1.2f, c - r, c + r * 0.2f)
                    close()
                }, fg)
            }
            IconShape.WUERFEL -> {
                canvas.drawPath(PaintKit.diamond(c, c - r * 0.5f, r, r * 0.5f), fg)
                canvas.drawRect(c - r, c - r * 0.5f, c, c + r * 0.7f, PaintKit.solid(PaintKit.shade(color, 1.35f)))
                canvas.drawRect(c, c - r * 0.5f, c + r, c + r * 0.7f, PaintKit.solid(PaintKit.shade(color, 1.5f)))
            }
            IconShape.BLITZ -> {
                canvas.drawPath(Path().apply {
                    moveTo(c + r * 0.4f, c - r * 1.1f)
                    lineTo(c - r * 0.5f, c + r * 0.15f)
                    lineTo(c - r * 0.05f, c + r * 0.15f)
                    lineTo(c - r * 0.4f, c + r * 1.1f)
                    lineTo(c + r * 0.5f, c - r * 0.1f)
                    lineTo(c + r * 0.05f, c - r * 0.1f)
                    close()
                }, fg)
            }
            IconShape.HERZ -> {
                canvas.drawPath(Path().apply {
                    moveTo(c, c + r)
                    cubicTo(c - r * 1.4f, c - r * 0.1f, c - r * 0.6f, c - r * 1.1f, c, c - r * 0.35f)
                    cubicTo(c + r * 0.6f, c - r * 1.1f, c + r * 1.4f, c - r * 0.1f, c, c + r)
                    close()
                }, fg)
            }
        }

        // Optionale Glyphe klein in der Ecke
        if (spec.glyph.isNotBlank()) {
            val text = PaintKit.text
            text.textSize = size * 0.24f
            text.color = PaintKit.WEISS
            canvas.drawText(spec.glyph, size - pad - size * 0.14f, size - pad - size * 0.08f, text)
        }
    }

    private fun star(cx: Float, cy: Float, r: Float): Path = Path().apply {
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r else r * 0.45f
            val angle = Math.toRadians(i * 36.0 - 90.0)
            val x = cx + radius * Math.cos(angle).toFloat()
            val y = cy + radius * Math.sin(angle).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}
