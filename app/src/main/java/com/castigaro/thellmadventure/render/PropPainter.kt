package com.castigaro.thellmadventure.render

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.castigaro.thellmadventure.world.Palette
import com.castigaro.thellmadventure.world.Prop
import com.castigaro.thellmadventure.world.PropType
import com.castigaro.thellmadventure.world.SLOT_COLORS
import kotlin.math.sin

/**
 * Malt jede Requisite prozedural aus Vektorformen — pro [PropType] eine
 * eigene Silhouette, eingefärbt über Palette und Farb-Slot. Die KI wählt
 * nur Typ, Position und Farbe; gezeichnet wird hier deterministisch.
 */
object PropPainter {

    private val TW = IsoMath.TILE_W
    private val TH = IsoMath.TILE_H

    fun draw(canvas: Canvas, prop: Prop, palette: Palette, timeMs: Long) {
        val cx = IsoMath.tileCenterX(prop.tx, prop.ty)
        val cy = IsoMath.tileCenterY(prop.tx, prop.ty)
        val color = SLOT_COLORS[prop.colorSlot.coerceIn(0, SLOT_COLORS.size - 1)]

        PaintKit.dropShadow(canvas, cx, cy, TW * 0.30f)

        when (prop.type) {
            PropType.KISTE -> kiste(canvas, cx, cy, color, prop)
            PropType.TRUHE -> truhe(canvas, cx, cy, color, prop)
            PropType.FASS -> fass(canvas, cx, cy, color)
            PropType.REGAL -> regal(canvas, cx, cy, color)
            PropType.TISCH -> tisch(canvas, cx, cy, color)
            PropType.STUHL -> stuhl(canvas, cx, cy, color)
            PropType.BETT -> bett(canvas, cx, cy, color)
            PropType.OFEN -> ofen(canvas, cx, cy, color, prop, timeMs)
            PropType.KAMIN -> kamin(canvas, cx, cy, color, prop, timeMs)
            PropType.TERMINAL -> terminal(canvas, cx, cy, color, palette, prop, timeMs)
            PropType.KONSOLE -> konsole(canvas, cx, cy, color, palette, prop, timeMs)
            PropType.HEBEL -> hebel(canvas, cx, cy, color, prop)
            PropType.ROHR -> rohr(canvas, cx, cy, color)
            PropType.LAMPE -> lampe(canvas, cx, cy, color, prop, timeMs)
            PropType.STATUE -> statue(canvas, cx, cy, color)
            PropType.BRUNNEN -> brunnen(canvas, cx, cy, color, palette)
            PropType.KESSEL -> kessel(canvas, cx, cy, color)
            PropType.PFLANZE -> pflanze(canvas, cx, cy, color)
            PropType.KAKTUS -> kaktus(canvas, cx, cy)
            PropType.PILZ -> pilz(canvas, cx, cy, color)
            PropType.KRISTALL -> kristall(canvas, cx, cy, color, timeMs)
            PropType.STEIN -> stein(canvas, cx, cy)
            PropType.TEPPICH -> teppich(canvas, cx, cy, color)
            PropType.SCHILD -> schild(canvas, cx, cy, color)
            PropType.SPIEGEL -> spiegel(canvas, cx, cy, color, palette)
            PropType.ALTAR -> altar(canvas, cx, cy, color, palette)
            PropType.ANKER -> anker(canvas, cx, cy, color)
            PropType.SEIL -> seil(canvas, cx, cy, color)
            PropType.LEITER -> leiter(canvas, cx, cy, color)
            PropType.UHR -> uhr(canvas, cx, cy, color)
        }

        drawStateBadges(canvas, cx, cy, prop, palette)
        if (prop.itemId != null) drawGlint(canvas, cx, cy - TH * 1.1f, timeMs)
    }

    // ---- Silhouetten -------------------------------------------------------

    private fun kiste(canvas: Canvas, cx: Float, cy: Float, color: Int, prop: Prop) {
        val hw = TW * 0.30f
        val hh = TH * 0.30f
        val h = TH * 0.70f
        PaintKit.cube(canvas, cx, cy, hw, hh, h, color)
        // Bretter-Fugen auf beiden Sichtflächen
        val fuge = PaintKit.line(PaintKit.shade(color, 0.55f), TH * 0.035f)
        for (i in 1..2) {
            val dy = h * i / 3f
            canvas.drawLine(cx - hw, cy - dy, cx, cy + hh - dy, fuge)
            canvas.drawLine(cx + hw, cy - dy, cx, cy + hh - dy, fuge)
        }
        // Deckelrahmen + Kanten
        canvas.drawPath(
            PaintKit.diamond(cx, cy - h, hw * 0.68f, hh * 0.68f),
            PaintKit.line(PaintKit.shade(color, 0.6f), TH * 0.035f),
        )
        PaintKit.cubeEdges(canvas, cx, cy, hw, hh, h, color, TH * 0.035f)
        if (prop.states["offen"] == true) {
            canvas.drawPath(PaintKit.diamond(cx, cy - h, TW * 0.24f, TH * 0.24f), PaintKit.solid(0xFF20140C.toInt()))
        }
    }

    private fun truhe(canvas: Canvas, cx: Float, cy: Float, color: Int, prop: Prop) {
        val hw = TW * 0.32f
        val hh = TH * 0.32f
        val bodyH = TH * 0.50f
        val open = prop.states["offen"] == true
        PaintKit.cube(canvas, cx, cy, hw, hh, bodyH, color)
        // Metallbänder über beide Sichtflächen
        val band = PaintKit.line(PaintKit.shade(color, 0.40f), TH * 0.05f)
        floatArrayOf(0.35f, 0.65f).forEach { f ->
            val yTop = (cy - bodyH) + hh * f
            canvas.drawLine(cx - hw * (1f - f), yTop, cx - hw * (1f - f), yTop + bodyH, band)
            canvas.drawLine(cx + hw * (1f - f), yTop, cx + hw * (1f - f), yTop + bodyH, band)
        }
        if (open) {
            canvas.drawPath(PaintKit.diamond(cx, cy - bodyH, hw * 0.78f, hh * 0.78f), PaintKit.solid(0xFF20140C.toInt()))
        }
        // Gewölbter Deckel (bei offener Truhe angehoben)
        val lidY = cy - bodyH - (if (open) TH * 0.42f else 0f)
        canvas.drawPath(PaintKit.diamond(cx, lidY, hw * 1.04f, hh * 1.04f), PaintKit.solid(PaintKit.shade(color, 1.22f)))
        canvas.drawPath(Path().apply {
            moveTo(cx - hw, lidY)
            quadTo(cx, lidY - TH * 0.34f, cx + hw, lidY)
            close()
        }, PaintKit.solid(PaintKit.shade(color, 1.35f)))
        // Schlossplatte mit Schlüsselloch
        val lockY = cy + hh * 0.4f - bodyH * 0.45f
        canvas.drawPath(
            PaintKit.roundedRect(cx - TH * 0.09f, lockY - TH * 0.11f, cx + TH * 0.09f, lockY + TH * 0.11f, TH * 0.03f),
            PaintKit.solid(PaintKit.GOLD),
        )
        canvas.drawCircle(cx, lockY - TH * 0.02f, TH * 0.035f, PaintKit.solid(0xFF20140C.toInt()))
        PaintKit.cubeEdges(canvas, cx, cy, hw, hh, bodyH, color, TH * 0.035f)
    }

    private fun fass(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        PaintKit.cylinder(canvas, cx, cy, TW * 0.22f, TH * 0.85f, color)
        val band = PaintKit.line(PaintKit.shade(color, 0.55f), TH * 0.06f)
        canvas.drawLine(cx - TW * 0.22f, cy - TH * 0.30f, cx + TW * 0.22f, cy - TH * 0.30f, band)
        canvas.drawLine(cx - TW * 0.22f, cy - TH * 0.60f, cx + TW * 0.22f, cy - TH * 0.60f, band)
    }

    private fun regal(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val hw = TW * 0.30f
        val hh = TH * 0.30f
        val h = TH * 1.60f
        val topY = cy - h
        PaintKit.cube(canvas, cx, cy, hw, hh, h, PaintKit.shade(color, 0.92f))
        // Offene Front (rechte Sichtfläche): dunkle Nische mit Regalböden
        fun px(f: Float) = cx + hw * f
        fun py(f: Float, g: Float) = (topY + hh * (1f - f)) + h * g
        canvas.drawPath(Path().apply {
            moveTo(px(0.12f), py(0.12f, 0.06f))
            lineTo(px(0.88f), py(0.88f, 0.06f))
            lineTo(px(0.88f), py(0.88f, 0.94f))
            lineTo(px(0.12f), py(0.12f, 0.94f))
            close()
        }, PaintKit.solid(PaintKit.shade(color, 0.45f)))
        val board = PaintKit.line(PaintKit.shade(color, 1.15f), TH * 0.05f)
        floatArrayOf(0.36f, 0.65f).forEach { g ->
            canvas.drawLine(px(0.12f), py(0.12f, g), px(0.88f), py(0.88f, g), board)
        }
        // Angedeuteter Inhalt: Bücher/Krüge auf den Böden
        val tints = intArrayOf(PaintKit.shade(color, 1.45f), PaintKit.GOLD, PaintKit.shade(color, 1.1f))
        floatArrayOf(0.36f, 0.65f, 0.94f).forEachIndexed { row, g ->
            for (i in 0..2) {
                val f = 0.22f + i * 0.24f
                val bx = px(f)
                val base = py(f, g) - TH * 0.02f
                canvas.drawRect(
                    bx - TH * 0.05f, base - TH * 0.22f, bx + TH * 0.05f, base,
                    PaintKit.solid(tints[(row + i) % tints.size]),
                )
            }
        }
        PaintKit.cubeEdges(canvas, cx, cy, hw, hh, h, color, TH * 0.035f)
    }

    private fun tisch(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val leg = PaintKit.solid(PaintKit.shade(color, 0.65f))
        canvas.drawRect(cx - TW * 0.24f, cy - TH * 0.55f, cx - TW * 0.18f, cy, leg)
        canvas.drawRect(cx + TW * 0.18f, cy - TH * 0.55f, cx + TW * 0.24f, cy, leg)
        canvas.drawPath(PaintKit.diamond(cx, cy - TH * 0.55f, TW * 0.34f, TH * 0.30f), PaintKit.solid(PaintKit.shade(color, 1.1f)))
    }

    private fun stuhl(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        PaintKit.cube(canvas, cx, cy, TW * 0.16f, TH * 0.16f, TH * 0.38f, color)
        canvas.drawRect(cx - TW * 0.16f, cy - TH * 0.95f, cx - TW * 0.08f, cy - TH * 0.38f, PaintKit.solid(PaintKit.shade(color, 0.8f)))
    }

    private fun bett(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val hw = TW * 0.36f
        val hh = TH * 0.36f
        // Holzrahmen + weiße Matratze
        PaintKit.cube(canvas, cx, cy, hw, hh, TH * 0.30f, 0xFF8A5A34.toInt())
        val my = cy - TH * 0.32f
        canvas.drawPath(PaintKit.diamond(cx, my, hw * 0.94f, hh * 0.94f), PaintKit.solid(PaintKit.WEISS))
        // Decke in Requisitenfarbe über der Fußhälfte
        canvas.drawPath(Path().apply {
            moveTo(cx + hw * 0.94f, my)
            lineTo(cx, my + hh * 0.94f)
            lineTo(cx - hw * 0.94f, my)
            close()
        }, PaintKit.solid(color))
        canvas.drawLine(cx - hw * 0.94f, my, cx + hw * 0.94f, my, PaintKit.line(PaintKit.shade(color, 0.7f), TH * 0.04f))
        // Kissen am Kopfende
        canvas.drawPath(
            PaintKit.diamond(cx - hw * 0.45f, my - hh * 0.45f, TW * 0.11f, TH * 0.11f),
            PaintKit.solid(PaintKit.shade(PaintKit.WEISS, 0.94f)),
        )
        PaintKit.cubeEdges(canvas, cx, cy, hw, hh, TH * 0.30f, 0xFF8A5A34.toInt(), TH * 0.03f)
    }

    private fun ofen(canvas: Canvas, cx: Float, cy: Float, color: Int, prop: Prop, timeMs: Long) {
        val body = PaintKit.shade(color, 0.6f)
        PaintKit.cube(canvas, cx, cy, TW * 0.28f, TH * 0.28f, TH * 0.90f, body)
        // Ofenrohr aus dem Deckel
        PaintKit.cylinder(canvas, cx + TW * 0.10f, cy - TH * 0.86f, TW * 0.05f, TH * 0.45f, PaintKit.shade(color, 0.45f))
        // Ofentür mit glühendem Bullauge
        canvas.drawPath(
            PaintKit.roundedRect(cx - TW * 0.17f, cy - TH * 0.62f, cx + TW * 0.02f, cy - TH * 0.16f, TH * 0.06f),
            PaintKit.solid(PaintKit.shade(color, 0.4f)),
        )
        val glow = if (prop.states["an"] == true) pulse(timeMs, 200, 255) else 70
        canvas.drawCircle(cx - TW * 0.075f, cy - TH * 0.39f, TH * 0.13f, PaintKit.solid(PaintKit.withAlpha(0xFFE06A2C.toInt(), glow)))
        PaintKit.cubeEdges(canvas, cx, cy, TW * 0.28f, TH * 0.28f, TH * 0.90f, body, TH * 0.035f)
    }

    private fun kamin(canvas: Canvas, cx: Float, cy: Float, color: Int, prop: Prop, timeMs: Long) {
        PaintKit.cube(canvas, cx, cy, TW * 0.32f, TH * 0.32f, TH * 1.40f, PaintKit.shade(color, 0.55f))
        if (prop.states["an"] == true) {
            val flameH = TH * (0.30f + 0.08f * sin(timeMs / 120.0).toFloat())
            canvas.drawPath(Path().apply {
                moveTo(cx - TH * 0.14f, cy - TH * 0.15f)
                quadTo(cx, cy - TH * 0.15f - flameH * 2f, cx + TH * 0.14f, cy - TH * 0.15f)
                close()
            }, PaintKit.solid(0xFFE0A73C.toInt()))
        }
    }

    private fun terminal(canvas: Canvas, cx: Float, cy: Float, color: Int, palette: Palette, prop: Prop, timeMs: Long) {
        val body = PaintKit.shade(color, 0.7f)
        PaintKit.cube(canvas, cx, cy, TW * 0.24f, TH * 0.24f, TH * 1.05f, body)
        val on = prop.states["an"] == true
        // Bildschirm mit dunklem Gehäuserahmen
        canvas.drawRect(cx - TW * 0.17f, cy - TH * 1.06f, cx + TW * 0.05f, cy - TH * 0.54f, PaintKit.solid(PaintKit.shade(color, 0.35f)))
        val screen = if (on) palette.accent else PaintKit.shade(palette.accent, 0.3f)
        canvas.drawRect(cx - TW * 0.14f, cy - TH * 1.00f, cx + TW * 0.02f, cy - TH * 0.60f, PaintKit.solid(screen))
        if (on) {
            // Textzeilen + blinkender Cursor
            val lines = PaintKit.line(PaintKit.withAlpha(PaintKit.WEISS, 90), TH * 0.025f)
            for (i in 0..2) {
                val y = cy - TH * (0.93f - i * 0.09f)
                canvas.drawLine(cx - TW * 0.12f, y, cx - TW * (0.02f + i * 0.02f), y, lines)
            }
            val blink = pulse(timeMs, 120, 255)
            canvas.drawRect(cx - TW * 0.12f, cy - TH * 0.70f, cx - TW * 0.085f, cy - TH * 0.65f, PaintKit.solid(PaintKit.withAlpha(PaintKit.WEISS, blink)))
        }
        // Bedienpult mit Statuslicht
        canvas.drawPath(PaintKit.diamond(cx + TW * 0.10f, cy - TH * 0.35f, TW * 0.13f, TH * 0.13f), PaintKit.solid(PaintKit.shade(color, 0.9f)))
        canvas.drawCircle(cx + TW * 0.10f, cy - TH * 0.37f, TH * 0.04f, PaintKit.solid(if (on) 0xFF6FD08C.toInt() else 0xFF8A4030.toInt()))
        PaintKit.cubeEdges(canvas, cx, cy, TW * 0.24f, TH * 0.24f, TH * 1.05f, body, TH * 0.035f)
    }

    private fun konsole(canvas: Canvas, cx: Float, cy: Float, color: Int, palette: Palette, prop: Prop, timeMs: Long) {
        val body = PaintKit.shade(color, 0.7f)
        val h = TH * 0.45f
        PaintKit.cube(canvas, cx, cy, TW * 0.32f, TH * 0.32f, h, body)
        // Pultplatte mit Leuchtknöpfen und Schieberegler
        canvas.drawPath(PaintKit.diamond(cx, cy - h, TW * 0.27f, TH * 0.27f), PaintKit.solid(PaintKit.shade(color, 0.5f)))
        val on = prop.states["an"] == true
        for (i in 0..2) {
            val alpha = if (on) pulse(timeMs + i * 300L, 90, 255) else 60
            canvas.drawCircle(cx - TW * 0.14f + i * TW * 0.08f, cy - h - TH * 0.02f, TH * 0.05f, PaintKit.solid(PaintKit.withAlpha(palette.accent, alpha)))
        }
        val slot = PaintKit.line(PaintKit.shade(color, 0.35f), TH * 0.035f)
        canvas.drawLine(cx + TW * 0.06f, cy - h + TH * 0.10f, cx + TW * 0.20f, cy - h + TH * 0.03f, slot)
        canvas.drawCircle(cx + TW * 0.13f, cy - h + TH * 0.065f, TH * 0.045f, PaintKit.solid(PaintKit.GOLD))
        PaintKit.cubeEdges(canvas, cx, cy, TW * 0.32f, TH * 0.32f, h, body, TH * 0.035f)
    }

    private fun hebel(canvas: Canvas, cx: Float, cy: Float, color: Int, prop: Prop) {
        PaintKit.cube(canvas, cx, cy, TW * 0.14f, TH * 0.14f, TH * 0.25f, PaintKit.shade(color, 0.6f))
        val up = prop.states["an"] != true
        val tipX = if (up) cx - TW * 0.10f else cx + TW * 0.10f
        canvas.drawLine(cx, cy - TH * 0.25f, tipX, cy - TH * 0.80f, PaintKit.line(PaintKit.shade(color, 0.9f), TH * 0.07f))
        canvas.drawCircle(tipX, cy - TH * 0.80f, TH * 0.10f, PaintKit.solid(PaintKit.GOLD))
    }

    private fun rohr(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        PaintKit.cylinder(canvas, cx, cy, TW * 0.09f, TH * 1.60f, PaintKit.shade(color, 0.8f))
        canvas.drawRect(cx - TW * 0.09f, cy - TH * 1.60f, cx + TW * 0.24f, cy - TH * 1.42f, PaintKit.solid(PaintKit.shade(color, 0.9f)))
    }

    private fun lampe(canvas: Canvas, cx: Float, cy: Float, color: Int, prop: Prop, timeMs: Long) {
        canvas.drawLine(cx, cy, cx, cy - TH * 1.30f, PaintKit.line(PaintKit.shade(color, 0.6f), TH * 0.07f))
        val on = prop.states["an"] != false // Lampen leuchten standardmäßig
        if (on) {
            canvas.drawCircle(cx, cy - TH * 1.35f, TH * 0.30f, PaintKit.solid(PaintKit.withAlpha(0xFFF0E0B8.toInt(), pulse(timeMs, 40, 70))))
        }
        canvas.drawCircle(cx, cy - TH * 1.35f, TH * 0.14f, PaintKit.solid(if (on) 0xFFF0E0B8.toInt() else PaintKit.shade(color, 0.5f)))
    }

    private fun statue(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val stone = PaintKit.shade(color, 0.75f)
        PaintKit.cube(canvas, cx, cy, TW * 0.24f, TH * 0.24f, TH * 0.30f, stone)
        canvas.drawPath(Path().apply {
            moveTo(cx - TW * 0.10f, cy - TH * 0.30f)
            lineTo(cx - TW * 0.06f, cy - TH * 1.10f)
            lineTo(cx + TW * 0.06f, cy - TH * 1.10f)
            lineTo(cx + TW * 0.10f, cy - TH * 0.30f)
            close()
        }, PaintKit.solid(PaintKit.shade(stone, 1.15f)))
        canvas.drawCircle(cx, cy - TH * 1.22f, TH * 0.14f, PaintKit.solid(PaintKit.shade(stone, 1.15f)))
    }

    private fun brunnen(canvas: Canvas, cx: Float, cy: Float, color: Int, palette: Palette) {
        PaintKit.cylinder(canvas, cx, cy, TW * 0.28f, TH * 0.45f, PaintKit.shade(color, 0.7f))
        canvas.drawOval(RectF(cx - TW * 0.20f, cy - TH * 0.55f, cx + TW * 0.20f, cy - TH * 0.35f), PaintKit.solid(palette.accent))
    }

    private fun kessel(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        PaintKit.cylinder(canvas, cx, cy, TW * 0.24f, TH * 0.60f, PaintKit.shade(color, 0.5f))
        canvas.drawOval(RectF(cx - TW * 0.28f, cy - TH * 0.70f, cx + TW * 0.28f, cy - TH * 0.50f), PaintKit.solid(PaintKit.shade(color, 0.4f)))
    }

    private fun pflanze(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        PaintKit.cylinder(canvas, cx, cy, TW * 0.14f, TH * 0.30f, 0xFF8A5A34.toInt())
        val leaf = PaintKit.solid(0xFF2F7D46.toInt())
        canvas.drawOval(RectF(cx - TW * 0.20f, cy - TH * 1.05f, cx + TW * 0.04f, cy - TH * 0.25f), leaf)
        canvas.drawOval(RectF(cx - TW * 0.04f, cy - TH * 1.15f, cx + TW * 0.20f, cy - TH * 0.30f), leaf)
        canvas.drawCircle(cx, cy - TH * 0.95f, TH * 0.10f, PaintKit.solid(color))
    }

    private fun kaktus(canvas: Canvas, cx: Float, cy: Float) {
        val green = 0xFF2F7D46.toInt()
        PaintKit.cylinder(canvas, cx, cy, TW * 0.10f, TH * 1.10f, green)
        PaintKit.cylinder(canvas, cx - TW * 0.16f, cy - TH * 0.45f, TW * 0.06f, TH * 0.40f, PaintKit.shade(green, 0.85f))
        PaintKit.cylinder(canvas, cx + TW * 0.16f, cy - TH * 0.30f, TW * 0.06f, TH * 0.30f, PaintKit.shade(green, 0.85f))
    }

    private fun pilz(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        PaintKit.cylinder(canvas, cx, cy, TW * 0.08f, TH * 0.45f, PaintKit.WEISS)
        canvas.drawOval(RectF(cx - TW * 0.22f, cy - TH * 0.80f, cx + TW * 0.22f, cy - TH * 0.35f), PaintKit.solid(color))
        canvas.drawCircle(cx - TW * 0.08f, cy - TH * 0.60f, TH * 0.05f, PaintKit.solid(PaintKit.WEISS))
        canvas.drawCircle(cx + TW * 0.09f, cy - TH * 0.55f, TH * 0.04f, PaintKit.solid(PaintKit.WEISS))
    }

    private fun kristall(canvas: Canvas, cx: Float, cy: Float, color: Int, timeMs: Long) {
        val glow = pulse(timeMs, 120, 220)
        canvas.drawPath(Path().apply {
            moveTo(cx, cy - TH * 1.20f)
            lineTo(cx + TW * 0.14f, cy - TH * 0.40f)
            lineTo(cx, cy)
            lineTo(cx - TW * 0.14f, cy - TH * 0.40f)
            close()
        }, PaintKit.solid(PaintKit.withAlpha(color, glow)))
        canvas.drawPath(Path().apply {
            moveTo(cx + TW * 0.16f, cy - TH * 0.65f)
            lineTo(cx + TW * 0.26f, cy - TH * 0.25f)
            lineTo(cx + TW * 0.12f, cy)
            close()
        }, PaintKit.solid(PaintKit.withAlpha(PaintKit.shade(color, 1.2f), glow)))
    }

    private fun stein(canvas: Canvas, cx: Float, cy: Float) {
        val grau = 0xFF6E6152.toInt()
        canvas.drawPath(Path().apply {
            moveTo(cx - TW * 0.26f, cy)
            quadTo(cx - TW * 0.22f, cy - TH * 0.70f, cx, cy - TH * 0.62f)
            quadTo(cx + TW * 0.26f, cy - TH * 0.55f, cx + TW * 0.24f, cy)
            close()
        }, PaintKit.solid(grau))
        canvas.drawCircle(cx - TW * 0.06f, cy - TH * 0.35f, TH * 0.07f, PaintKit.solid(PaintKit.shade(grau, 1.2f)))
    }

    private fun teppich(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        canvas.drawPath(PaintKit.diamond(cx, cy, TW * 0.40f, TH * 0.40f), PaintKit.solid(color))
        canvas.drawPath(PaintKit.diamond(cx, cy, TW * 0.28f, TH * 0.28f), PaintKit.solid(PaintKit.shade(color, 1.2f)))
    }

    private fun schild(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        canvas.drawLine(cx, cy, cx, cy - TH * 0.90f, PaintKit.line(0xFF5E3B21.toInt(), TH * 0.07f))
        canvas.drawPath(PaintKit.roundedRect(cx - TW * 0.20f, cy - TH * 1.15f, cx + TW * 0.20f, cy - TH * 0.75f, TH * 0.08f), PaintKit.solid(color))
        // Angedeutete Schriftzeilen + Rahmen
        val ink = PaintKit.line(PaintKit.shade(color, 0.45f), TH * 0.035f)
        canvas.drawLine(cx - TW * 0.14f, cy - TH * 1.05f, cx + TW * 0.14f, cy - TH * 1.05f, ink)
        canvas.drawLine(cx - TW * 0.14f, cy - TH * 0.96f, cx + TW * 0.10f, cy - TH * 0.96f, ink)
        canvas.drawLine(cx - TW * 0.14f, cy - TH * 0.87f, cx + TW * 0.12f, cy - TH * 0.87f, ink)
        canvas.drawPath(
            PaintKit.roundedRect(cx - TW * 0.20f, cy - TH * 1.15f, cx + TW * 0.20f, cy - TH * 0.75f, TH * 0.08f),
            PaintKit.line(PaintKit.shade(color, 0.55f), TH * 0.03f),
        )
    }

    private fun spiegel(canvas: Canvas, cx: Float, cy: Float, color: Int, palette: Palette) {
        canvas.drawPath(PaintKit.roundedRect(cx - TW * 0.16f, cy - TH * 1.40f, cx + TW * 0.16f, cy - TH * 0.10f, TH * 0.15f), PaintKit.solid(PaintKit.shade(color, 0.7f)))
        canvas.drawPath(PaintKit.roundedRect(cx - TW * 0.11f, cy - TH * 1.30f, cx + TW * 0.11f, cy - TH * 0.20f, TH * 0.10f), PaintKit.solid(palette.bgTop))
        canvas.drawLine(cx - TW * 0.06f, cy - TH * 1.20f, cx + TW * 0.05f, cy - TH * 0.55f, PaintKit.line(PaintKit.withAlpha(PaintKit.WEISS, 120), TH * 0.05f))
    }

    private fun altar(canvas: Canvas, cx: Float, cy: Float, color: Int, palette: Palette) {
        PaintKit.cube(canvas, cx, cy, TW * 0.34f, TH * 0.34f, TH * 0.55f, PaintKit.shade(color, 0.8f))
        canvas.drawPath(PaintKit.diamond(cx, cy - TH * 0.62f, TW * 0.14f, TH * 0.14f), PaintKit.solid(palette.accent))
    }

    private fun anker(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val metal = PaintKit.line(PaintKit.shade(color, 0.6f), TH * 0.09f)
        canvas.drawLine(cx, cy - TH * 1.10f, cx, cy - TH * 0.15f, metal)
        canvas.drawCircle(cx, cy - TH * 1.12f, TH * 0.12f, PaintKit.line(PaintKit.shade(color, 0.6f), TH * 0.06f))
        canvas.drawLine(cx - TW * 0.16f, cy - TH * 0.80f, cx + TW * 0.16f, cy - TH * 0.80f, metal)
        canvas.drawPath(Path().apply {
            moveTo(cx - TW * 0.18f, cy - TH * 0.35f)
            quadTo(cx, cy + TH * 0.05f, cx + TW * 0.18f, cy - TH * 0.35f)
        }, metal)
    }

    private fun seil(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val rope = PaintKit.line(PaintKit.shade(color, 0.9f), TH * 0.09f)
        for (i in 0..2) {
            canvas.drawOval(RectF(cx - TW * 0.22f, cy - TH * (0.22f + i * 0.14f), cx + TW * 0.22f, cy + TH * 0.10f - TH * i * 0.14f), rope)
        }
    }

    private fun leiter(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        val holz = PaintKit.line(PaintKit.shade(color, 0.75f), TH * 0.06f)
        canvas.drawLine(cx - TW * 0.12f, cy, cx - TW * 0.12f, cy - TH * 1.60f, holz)
        canvas.drawLine(cx + TW * 0.12f, cy, cx + TW * 0.12f, cy - TH * 1.60f, holz)
        for (i in 1..4) {
            canvas.drawLine(cx - TW * 0.12f, cy - TH * 0.32f * i, cx + TW * 0.12f, cy - TH * 0.32f * i, holz)
        }
    }

    private fun uhr(canvas: Canvas, cx: Float, cy: Float, color: Int) {
        PaintKit.cube(canvas, cx, cy, TW * 0.16f, TH * 0.16f, TH * 1.50f, PaintKit.shade(color, 0.75f))
        canvas.drawCircle(cx - TW * 0.06f, cy - TH * 1.25f, TH * 0.15f, PaintKit.solid(PaintKit.WEISS))
        canvas.drawLine(cx - TW * 0.06f, cy - TH * 1.25f, cx - TW * 0.06f, cy - TH * 1.36f, PaintKit.line(0xFF20140C.toInt(), TH * 0.03f))
    }

    // ---- Zustands-Anzeiger und Funkeln -------------------------------------

    private fun drawStateBadges(canvas: Canvas, cx: Float, cy: Float, prop: Prop, palette: Palette) {
        if (prop.states["verschlossen"] == true) {
            canvas.drawCircle(cx + IsoMath.TILE_W * 0.22f, cy - IsoMath.TILE_H * 0.9f, IsoMath.TILE_H * 0.10f, PaintKit.solid(PaintKit.GOLD))
            canvas.drawCircle(cx + IsoMath.TILE_W * 0.22f, cy - IsoMath.TILE_H * 0.9f, IsoMath.TILE_H * 0.05f, PaintKit.solid(0xFF20140C.toInt()))
        }
        if (prop.states["kaputt"] == true) {
            val riss = PaintKit.line(0xCC20140C.toInt(), IsoMath.TILE_H * 0.045f)
            canvas.drawLine(cx - IsoMath.TILE_W * 0.08f, cy - IsoMath.TILE_H * 0.7f, cx, cy - IsoMath.TILE_H * 0.45f, riss)
            canvas.drawLine(cx, cy - IsoMath.TILE_H * 0.45f, cx - IsoMath.TILE_W * 0.06f, cy - IsoMath.TILE_H * 0.2f, riss)
        }
    }

    /** Kleines Funkeln über Requisiten, die einen Gegenstand bergen. */
    private fun drawGlint(canvas: Canvas, cx: Float, cy: Float, timeMs: Long) {
        val alpha = pulse(timeMs, 90, 255)
        val p = PaintKit.solid(PaintKit.withAlpha(PaintKit.GOLD, alpha))
        val r = IsoMath.TILE_H * 0.09f
        canvas.drawPath(Path().apply {
            moveTo(cx, cy - r * 2f)
            lineTo(cx + r * 0.5f, cy - r * 0.5f)
            lineTo(cx + r * 2f, cy)
            lineTo(cx + r * 0.5f, cy + r * 0.5f)
            lineTo(cx, cy + r * 2f)
            lineTo(cx - r * 0.5f, cy + r * 0.5f)
            lineTo(cx - r * 2f, cy)
            lineTo(cx - r * 0.5f, cy - r * 0.5f)
            close()
        }, p)
    }

    private fun pulse(timeMs: Long, min: Int, max: Int): Int {
        val t = (sin(timeMs / 350.0) + 1.0) / 2.0
        return (min + (max - min) * t).toInt()
    }
}
