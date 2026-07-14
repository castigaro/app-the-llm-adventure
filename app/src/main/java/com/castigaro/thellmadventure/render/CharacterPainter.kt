package com.castigaro.thellmadventure.render

import android.graphics.Canvas
import android.graphics.Path
import com.castigaro.thellmadventure.world.BodyShape
import com.castigaro.thellmadventure.world.Headgear
import com.castigaro.thellmadventure.world.Look
import com.castigaro.thellmadventure.world.SLOT_COLORS
import kotlin.math.abs
import kotlin.math.sin

/**
 * Malt Spieler und NPCs komplett aus Vektorformen — direkte Weiterentwicklung
 * von Kap-Krakenzahns HeroSprite, aber parametrisiert über das [Look]-Vokabular
 * (Körperform, zwei Farb-Slots, Kopfbedeckung), damit die KI zufällige
 * Charaktere als reine Daten erschaffen kann.
 */
object CharacterPainter {

    private const val HAUT = 0xFFE8C9A0.toInt()
    private const val AUGE = 0xFF20140C.toInt()

    /** Figurhöhe in Weltpixeln. */
    val FIG_H = IsoMath.TILE_H * 1.7f

    /**
     * Zeichnet eine Figur mit Fußpunkt an Weltposition ([wx], [wy]).
     * [phase] treibt die Gehanimation, [facingRight] spiegelt horizontal.
     */
    fun draw(
        canvas: Canvas, look: Look, wx: Float, wy: Float,
        phase: Float, facingRight: Boolean, walking: Boolean,
    ) {
        val figH = FIG_H
        val colorA = SLOT_COLORS[look.colorA.coerceIn(0, SLOT_COLORS.size - 1)]
        val colorB = SLOT_COLORS[look.colorB.coerceIn(0, SLOT_COLORS.size - 1)]

        PaintKit.dropShadow(canvas, wx, wy, figH * 0.22f)

        canvas.save()
        canvas.translate(wx, wy)
        if (!facingRight) canvas.scale(-1f, 1f)

        val swing = if (walking) sin(phase.toDouble()).toFloat() else 0f
        val bob = if (walking) abs(sin(phase.toDouble())).toFloat() * figH * 0.02f else 0f
        canvas.translate(0f, -bob)

        // Beine (Farb-Slot B, dunkler)
        val legColor = PaintKit.shade(colorB, 0.7f)
        val legW = figH * 0.11f
        val legTop = -figH * 0.42f
        leg(canvas, -swing * figH * 0.10f, legTop, legW, legColor)
        leg(canvas, swing * figH * 0.10f, legTop, legW, legColor)

        // Rumpf nach Körperform
        body(canvas, look.body, figH, colorA, colorB)

        // Vorderer Arm (schwingt)
        arm(canvas, swing * figH * 0.14f, -figH * 0.80f, figH, colorA)

        // Kopf
        val headR = figH * 0.11f
        val headY = -figH * 0.86f - headR
        canvas.drawCircle(0f, headY, headR, PaintKit.solid(HAUT))
        canvas.drawCircle(headR * 0.35f, headY, headR * 0.12f, PaintKit.solid(AUGE))

        headgear(canvas, look.headgear, headY, headR, colorB)

        canvas.restore()
    }

    private fun body(canvas: Canvas, shape: BodyShape, figH: Float, colorA: Int, colorB: Int) {
        val top = -figH * 0.86f
        val bottom = -figH * 0.40f
        val path = when (shape) {
            BodyShape.TRAPEZ -> poly(
                -figH * 0.16f to top, -figH * 0.20f to bottom,
                figH * 0.20f to bottom, figH * 0.16f to top,
            )
            BodyShape.ROBE -> poly(
                -figH * 0.14f to top, -figH * 0.30f to -figH * 0.02f,
                figH * 0.30f to -figH * 0.02f, figH * 0.14f to top,
            )
            BodyShape.BREIT -> poly(
                -figH * 0.24f to top, -figH * 0.26f to bottom,
                figH * 0.26f to bottom, figH * 0.24f to top,
            )
            BodyShape.SCHLANK -> poly(
                -figH * 0.11f to top, -figH * 0.13f to bottom,
                figH * 0.13f to bottom, figH * 0.11f to top,
            )
            BodyShape.RUND -> null // Kreis statt Polygon
            BodyShape.KANTIG -> poly(
                -figH * 0.20f to top, -figH * 0.20f to bottom,
                figH * 0.20f to bottom, figH * 0.20f to top,
            )
        }
        if (path != null) {
            canvas.drawPath(path, PaintKit.solid(colorA))
        } else {
            canvas.drawCircle(0f, (top + -figH * 0.40f) / 2f, figH * 0.26f, PaintKit.solid(colorA))
        }
        // Gürtel/Zierstreifen in Farb-Slot B
        canvas.drawRect(-figH * 0.18f, -figH * 0.46f, figH * 0.18f, -figH * 0.41f, PaintKit.solid(colorB))
    }

    private fun headgear(canvas: Canvas, gear: Headgear, headY: Float, headR: Float, color: Int) {
        when (gear) {
            Headgear.KEINE -> {
                // Etwas Haar
                canvas.drawCircle(-headR * 0.2f, headY - headR * 0.35f, headR * 0.8f, PaintKit.solid(PaintKit.shade(color, 0.5f)))
                canvas.drawCircle(0f, headY + headR * 0.05f, headR, PaintKit.solid(HAUT))
                canvas.drawCircle(headR * 0.35f, headY, headR * 0.12f, PaintKit.solid(AUGE))
            }
            Headgear.HUT -> {
                canvas.drawPath(Path().apply {
                    moveTo(-headR * 1.5f, headY - headR * 0.6f)
                    quadTo(0f, headY - headR * 2.0f, headR * 1.5f, headY - headR * 0.6f)
                    quadTo(0f, headY - headR * 0.3f, -headR * 1.5f, headY - headR * 0.6f)
                    close()
                }, PaintKit.solid(PaintKit.shade(color, 0.7f)))
            }
            Headgear.KAPUZE -> {
                canvas.drawCircle(0f, headY - headR * 0.1f, headR * 1.25f, PaintKit.solid(PaintKit.shade(color, 0.6f)))
                canvas.drawCircle(headR * 0.25f, headY + headR * 0.1f, headR * 0.85f, PaintKit.solid(HAUT))
                canvas.drawCircle(headR * 0.45f, headY + headR * 0.05f, headR * 0.12f, PaintKit.solid(AUGE))
            }
            Headgear.HELM -> {
                canvas.drawPath(Path().apply {
                    addArc(-headR * 1.15f, headY - headR * 1.3f, headR * 1.15f, headY + headR * 0.6f, 180f, 180f)
                    close()
                }, PaintKit.solid(PaintKit.shade(color, 0.85f)))
            }
            Headgear.KRONE -> {
                canvas.drawPath(Path().apply {
                    moveTo(-headR * 0.9f, headY - headR * 0.8f)
                    lineTo(-headR * 0.9f, headY - headR * 1.5f)
                    lineTo(-headR * 0.3f, headY - headR * 1.0f)
                    lineTo(0f, headY - headR * 1.6f)
                    lineTo(headR * 0.3f, headY - headR * 1.0f)
                    lineTo(headR * 0.9f, headY - headR * 1.5f)
                    lineTo(headR * 0.9f, headY - headR * 0.8f)
                    close()
                }, PaintKit.solid(PaintKit.GOLD))
            }
            Headgear.ANTENNE -> {
                canvas.drawLine(0f, headY - headR, 0f, headY - headR * 2.2f, PaintKit.line(PaintKit.shade(color, 0.8f), headR * 0.15f))
                canvas.drawCircle(0f, headY - headR * 2.3f, headR * 0.22f, PaintKit.solid(PaintKit.GOLD))
            }
        }
    }

    private fun leg(canvas: Canvas, dx: Float, top: Float, width: Float, color: Int) {
        val path = Path().apply {
            moveTo(dx - width / 2, top)
            lineTo(dx - width / 2 + dx * 0.3f, 0f)
            lineTo(dx + width / 2 + dx * 0.3f, 0f)
            lineTo(dx + width / 2, top)
            close()
        }
        canvas.drawPath(path, PaintKit.solid(color))
    }

    private fun arm(canvas: Canvas, dx: Float, shoulderY: Float, figH: Float, sleeve: Int) {
        val armW = figH * 0.08f
        val path = Path().apply {
            moveTo(figH * 0.13f, shoulderY)
            lineTo(figH * 0.13f + dx, shoulderY + figH * 0.34f)
            lineTo(figH * 0.13f + dx + armW, shoulderY + figH * 0.34f)
            lineTo(figH * 0.13f + armW, shoulderY)
            close()
        }
        canvas.drawPath(path, PaintKit.solid(PaintKit.shade(sleeve, 0.9f)))
        canvas.drawCircle(figH * 0.13f + dx + armW / 2, shoulderY + figH * 0.36f, armW * 0.6f, PaintKit.solid(HAUT))
    }

    private fun poly(vararg points: Pair<Float, Float>): Path = Path().apply {
        points.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
        close()
    }
}
