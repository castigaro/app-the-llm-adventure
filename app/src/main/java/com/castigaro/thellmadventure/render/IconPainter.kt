package com.castigaro.thellmadventure.render

import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import com.castigaro.thellmadventure.world.IconArchetype
import com.castigaro.thellmadventure.world.IconSpec
import com.castigaro.thellmadventure.world.SLOT_COLORS

/**
 * Malt für jeden Gegenstand ein wiedererkennbares Detail-Motiv auf eine
 * einheitlich dunkle Kachel — reine Vektorformen, kein Bild-Asset (Stil und
 * Kachel-Schema aus Kap-Krakenzahns ItemIconPainter). Alle Koordinaten sind
 * Anteile der Kachelgröße, damit alle dp-Größen identisch skalieren.
 *
 * Anders als bei Kap-Krakenzahn sind die Gegenstände KI-erfunden: Statt einer
 * Zeichnung je Item gibt es eine je [IconArchetype]-Motiv, parametrisiert über
 * Material-Grundfarbe, Akzentfarbe und die Flaggen leuchtet/abgenutzt.
 */
object IconPainter {

    // Einheitliche Kachel: etwas heller als die Leiste, Rand zur Abgrenzung.
    private const val KACHEL = 0xFF262433.toInt()
    private const val KACHEL_LICHT = 0xFF34324A.toInt()
    private const val KACHEL_RAND = 0xFF403D5C.toInt()

    // Feste Motiv-Farben (natürliche Töne, unabhängig von Material/Akzent).
    private const val HOLZ = 0xFF8A5A34.toInt()
    private const val HOLZ_DUNKEL = 0xFF5E3B21.toInt()
    private const val TAU = 0xFFC7A26A.toInt()
    private const val FLAMME_A = 0xFFE86A2C.toInt()
    private const val FLAMME_B = 0xFFF2C24E.toInt()
    private const val CHILI = 0xFFD8402C.toInt()
    private const val GRUEN = 0xFF3F7D3A.toInt()
    private const val GLAS_BLAU = 0xFF9FD4E8.toInt()
    private const val TINTE = 0xFF20242E.toInt()

    /** Malt Kachel + Motiv in das Quadrat (0,0)–(size,size). */
    fun draw(canvas: Canvas, spec: IconSpec, name: String, size: Float, selected: Boolean = false) {
        val pad = size * 0.1f
        val radius = size * 0.18f
        val kachel = PaintKit.roundedRect(pad, pad, size - pad, size - pad, radius)
        canvas.drawPath(kachel, PaintKit.solid(KACHEL))
        canvas.drawPath(
            PaintKit.roundedRect(pad, pad, size - pad, size * 0.32f, radius),
            PaintKit.solid(KACHEL_LICHT),
        )
        canvas.drawPath(kachel, PaintKit.line(KACHEL_RAND, size * 0.02f))

        val (motiv, material) = spec.motivUndMaterial(name)
        val grund = material.grund
        val akzent = SLOT_COLORS[spec.colorIdx.coerceIn(0, SLOT_COLORS.size - 1)]

        if (spec.leuchtet) {
            PaintKit.radialGlow(canvas, size * 0.5f, size * 0.52f, size * 0.36f, PaintKit.withAlpha(akzent, 110))
        }
        motif(canvas, motiv, size, grund, akzent)
        if (spec.abgenutzt) kratzer(canvas, size)

        if (selected) {
            canvas.drawPath(kachel, PaintKit.line(PaintKit.GOLD, size * 0.045f))
        }
    }

    /**
     * Motiv-Dispatch — absichtlich als Expression ohne else: Ein neues
     * [IconArchetype] erzwingt hier eine Ergänzung, statt still leer zu bleiben.
     */
    private fun motif(canvas: Canvas, motiv: IconArchetype, s: Float, grund: Int, akzent: Int): Unit = when (motiv) {
        IconArchetype.SCHLUESSEL -> schluessel(canvas, s, grund)
        IconArchetype.FLASCHE -> flasche(canvas, s, grund, akzent)
        IconArchetype.BUCH -> buch(canvas, s, grund, akzent)
        IconArchetype.SCHRIFTROLLE -> schriftrolle(canvas, s, grund, akzent)
        IconArchetype.WERKZEUG -> werkzeug(canvas, s, grund)
        IconArchetype.KLINGE -> klinge(canvas, s, grund)
        IconArchetype.FACKEL -> fackel(canvas, s)
        IconArchetype.LATERNE -> laterne(canvas, s, grund)
        IconArchetype.SEIL -> seil(canvas, s)
        IconArchetype.HAKEN -> haken(canvas, s, grund)
        IconArchetype.ZAHNRAD -> zahnrad(canvas, s, grund)
        IconArchetype.KRISTALL -> kristall(canvas, s, akzent)
        IconArchetype.MUENZE -> muenze(canvas, s, grund)
        IconArchetype.AMULETT -> amulett(canvas, s, grund, akzent)
        IconArchetype.RING -> ring(canvas, s, grund, akzent)
        IconArchetype.KARTE -> karte(canvas, s, grund, akzent)
        IconArchetype.KOMPASS -> kompass(canvas, s, grund)
        IconArchetype.HORN -> horn(canvas, s, grund)
        IconArchetype.FEDER -> feder(canvas, s, akzent)
        IconArchetype.KRAUT -> kraut(canvas, s, akzent)
        IconArchetype.PILZ -> pilz(canvas, s, akzent)
        IconArchetype.KNOCHEN -> knochen(canvas, s, grund)
        IconArchetype.BEUTEL -> beutel(canvas, s, grund, akzent)
        IconArchetype.KRUG -> krug(canvas, s, grund, akzent)
        IconArchetype.SPIEGEL -> spiegel(canvas, s, grund)
        IconArchetype.GLOCKE -> glocke(canvas, s, grund)
        IconArchetype.STEIN -> stein(canvas, s, grund)
        IconArchetype.WUERFEL -> wuerfel(canvas, s, grund, akzent)
    }

    /** Abnutzung: leichte Trübung plus drei Kratzspuren quer über das Motiv. */
    private fun kratzer(canvas: Canvas, s: Float) {
        canvas.drawPath(
            PaintKit.roundedRect(s * 0.1f, s * 0.1f, s * 0.9f, s * 0.9f, s * 0.18f),
            PaintKit.solid(PaintKit.withAlpha(0xFF14121A.toInt(), 50)),
        )
        val k = PaintKit.line(PaintKit.withAlpha(0xFF14121A.toInt(), 170), 0.02f * s)
        canvas.drawLine(0.30f * s, 0.34f * s, 0.44f * s, 0.50f * s, k)
        canvas.drawLine(0.58f * s, 0.40f * s, 0.70f * s, 0.56f * s, k)
        canvas.drawLine(0.36f * s, 0.62f * s, 0.52f * s, 0.72f * s, k)
    }

    // ---- Motive --------------------------------------------------------------

    /** Schlüssel mit Ring-Reide, Schaft und zwei Bartzähnen. */
    private fun schluessel(canvas: Canvas, s: Float, grund: Int) {
        canvas.drawCircle(0.30f * s, 0.50f * s, 0.10f * s, PaintKit.line(grund, 0.055f * s))
        canvas.drawPath(
            PaintKit.roundedRect(0.38f * s, 0.465f * s, 0.78f * s, 0.535f * s, 0.03f * s),
            PaintKit.solid(grund),
        )
        canvas.drawRect(0.63f * s, 0.535f * s, 0.69f * s, 0.66f * s, PaintKit.solid(grund))
        canvas.drawRect(0.72f * s, 0.535f * s, 0.78f * s, 0.62f * s, PaintKit.solid(grund))
        // Glanzpunkt auf der Reide
        canvas.drawCircle(0.27f * s, 0.46f * s, 0.02f * s, PaintKit.solid(PaintKit.withAlpha(PaintKit.WEISS, 190)))
    }

    /** Flasche mit Korken; die Füllung trägt die Akzentfarbe. */
    private fun flasche(canvas: Canvas, s: Float, grund: Int, akzent: Int) {
        canvas.drawPath(
            PaintKit.roundedRect(0.455f * s, 0.22f * s, 0.545f * s, 0.32f * s, 0.02f * s),
            PaintKit.solid(HOLZ),
        )
        canvas.drawRect(0.45f * s, 0.30f * s, 0.55f * s, 0.44f * s, PaintKit.solid(grund))
        canvas.drawPath(
            PaintKit.roundedRect(0.35f * s, 0.42f * s, 0.65f * s, 0.78f * s, 0.09f * s),
            PaintKit.solid(grund),
        )
        canvas.drawPath(
            PaintKit.roundedRect(0.38f * s, 0.55f * s, 0.62f * s, 0.75f * s, 0.06f * s),
            PaintKit.solid(akzent),
        )
        canvas.drawLine(
            0.41f * s, 0.47f * s, 0.41f * s, 0.70f * s,
            PaintKit.line(PaintKit.withAlpha(PaintKit.WEISS, 150), 0.025f * s),
        )
    }

    /** Geschlossenes Buch: Einband, Buchrücken, Seitenschnitt, Akzent-Emblem. */
    private fun buch(canvas: Canvas, s: Float, grund: Int, akzent: Int) {
        canvas.drawPath(
            PaintKit.roundedRect(0.30f * s, 0.30f * s, 0.72f * s, 0.74f * s, 0.03f * s),
            PaintKit.solid(grund),
        )
        // Seitenschnitt rechts
        canvas.drawRect(0.66f * s, 0.33f * s, 0.70f * s, 0.71f * s, PaintKit.solid(0xFFE8E0CC.toInt()))
        // Buchrücken links
        canvas.drawPath(
            PaintKit.roundedRect(0.30f * s, 0.30f * s, 0.38f * s, 0.74f * s, 0.03f * s),
            PaintKit.solid(PaintKit.shade(grund, 0.7f)),
        )
        // Emblem und Schließenband
        canvas.drawPath(PaintKit.diamond(0.52f * s, 0.52f * s, 0.07f * s, 0.09f * s), PaintKit.solid(akzent))
        canvas.drawLine(0.38f * s, 0.62f * s, 0.66f * s, 0.62f * s, PaintKit.line(PaintKit.shade(grund, 0.7f), 0.02f * s))
    }

    /** Schriftrolle: Pergament mit eingerollten Enden und Siegelband. */
    private fun schriftrolle(canvas: Canvas, s: Float, grund: Int, akzent: Int) {
        canvas.drawRect(0.34f * s, 0.34f * s, 0.66f * s, 0.70f * s, PaintKit.solid(grund))
        canvas.drawOval(RectF(0.30f * s, 0.26f * s, 0.70f * s, 0.38f * s), PaintKit.solid(PaintKit.lighten(grund, 20)))
        canvas.drawOval(RectF(0.30f * s, 0.66f * s, 0.70f * s, 0.78f * s), PaintKit.solid(PaintKit.shade(grund, 0.82f)))
        // Schriftzeilen
        val zeile = PaintKit.line(PaintKit.withAlpha(TINTE, 160), 0.018f * s)
        canvas.drawLine(0.39f * s, 0.45f * s, 0.61f * s, 0.45f * s, zeile)
        canvas.drawLine(0.39f * s, 0.52f * s, 0.58f * s, 0.52f * s, zeile)
        // Siegelband
        canvas.drawRect(0.34f * s, 0.58f * s, 0.66f * s, 0.63f * s, PaintKit.solid(akzent))
    }

    /** Werkzeug: diagonaler Hammer mit Holzstiel und Metallkopf. */
    private fun werkzeug(canvas: Canvas, s: Float, grund: Int) {
        canvas.save()
        canvas.rotate(-35f, 0.5f * s, 0.5f * s)
        canvas.drawPath(
            PaintKit.roundedRect(0.465f * s, 0.34f * s, 0.535f * s, 0.80f * s, 0.03f * s),
            PaintKit.solid(HOLZ),
        )
        canvas.drawRect(0.475f * s, 0.36f * s, 0.495f * s, 0.78f * s, PaintKit.solid(PaintKit.lighten(HOLZ, 30)))
        canvas.drawPath(
            PaintKit.roundedRect(0.32f * s, 0.24f * s, 0.68f * s, 0.38f * s, 0.03f * s),
            PaintKit.solid(grund),
        )
        canvas.drawRect(0.34f * s, 0.25f * s, 0.66f * s, 0.29f * s, PaintKit.solid(PaintKit.lighten(grund, 30)))
        canvas.restore()
    }

    /** Klinge: Machete mit Griff und Schneiden-Glanz (Muster aus Kap-Krakenzahn). */
    private fun klinge(canvas: Canvas, s: Float, grund: Int) {
        canvas.save()
        canvas.rotate(-35f, 0.5f * s, 0.5f * s)
        val stahl = PaintKit.lighten(grund, 60)
        val blatt = Path().apply {
            moveTo(0.30f * s, 0.46f * s)
            lineTo(0.74f * s, 0.46f * s)
            quadTo(0.84f * s, 0.50f * s, 0.72f * s, 0.58f * s)
            lineTo(0.30f * s, 0.56f * s)
            close()
        }
        canvas.drawPath(blatt, PaintKit.solid(stahl))
        canvas.drawLine(
            0.32f * s, 0.565f * s, 0.72f * s, 0.575f * s,
            PaintKit.line(PaintKit.withAlpha(PaintKit.WEISS, 200), 0.018f * s),
        )
        canvas.drawPath(
            PaintKit.roundedRect(0.14f * s, 0.455f * s, 0.30f * s, 0.565f * s, 0.04f * s),
            PaintKit.solid(0xFF4A3B2A.toInt()),
        )
        canvas.restore()
    }

    /** Fackel: diagonaler Ast mit Wicklung und zweifarbiger Flamme. */
    private fun fackel(canvas: Canvas, s: Float) {
        canvas.save()
        canvas.rotate(-40f, 0.5f * s, 0.5f * s)
        canvas.drawPath(
            PaintKit.roundedRect(0.455f * s, 0.28f * s, 0.545f * s, 0.82f * s, 0.04f * s),
            PaintKit.solid(HOLZ),
        )
        canvas.drawRect(0.465f * s, 0.30f * s, 0.49f * s, 0.80f * s, PaintKit.solid(PaintKit.lighten(HOLZ, 30)))
        canvas.drawRect(0.44f * s, 0.26f * s, 0.56f * s, 0.36f * s, PaintKit.solid(HOLZ_DUNKEL))
        flamme(canvas, 0.5f * s, 0.14f * s, 0.10f * s)
        canvas.restore()
    }

    /** Sturmlaterne mit warmem Glas, Flamme und Metallrahmen. */
    private fun laterne(canvas: Canvas, s: Float, grund: Int) {
        val fenster = PaintKit.roundedRect(0.34f * s, 0.38f * s, 0.66f * s, 0.74f * s, 0.04f * s)
        canvas.drawPath(fenster, PaintKit.solid(FLAMME_B))
        flamme(canvas, 0.5f * s, 0.58f * s, 0.09f * s)
        val rahmen = PaintKit.line(grund, 0.045f * s)
        canvas.drawPath(fenster, rahmen)
        canvas.drawLine(0.5f * s, 0.38f * s, 0.5f * s, 0.74f * s, rahmen)
        canvas.drawRect(0.38f * s, 0.32f * s, 0.62f * s, 0.38f * s, PaintKit.solid(grund))
        canvas.drawCircle(0.5f * s, 0.26f * s, 0.055f * s, PaintKit.line(grund, 0.04f * s))
    }

    /** Aufgeschossene Seilrolle mit herausragendem Ende. */
    private fun seil(canvas: Canvas, s: Float) {
        val cx = 0.48f * s
        val cy = 0.50f * s
        val r = 0.20f * s
        canvas.drawCircle(cx, cy, r, PaintKit.line(TAU, r * 0.55f))
        val rille = PaintKit.line(PaintKit.shade(TAU, 0.75f), r * 0.09f)
        canvas.drawCircle(cx, cy, r * 0.82f, rille)
        canvas.drawCircle(cx, cy, r * 1.18f, rille)
        val ende = Path().apply {
            moveTo(cx + r * 0.6f, cy + r * 0.9f)
            quadTo(cx + r * 1.1f, cy + r * 1.2f, cx + r * 1.4f, cy + r * 1.1f)
        }
        canvas.drawPath(ende, PaintKit.line(TAU, r * 0.3f))
    }

    /** Dreizinkiger Enterhaken mit Öse. */
    private fun haken(canvas: Canvas, s: Float, grund: Int) {
        val cx = 0.5f * s
        val cy = 0.52f * s
        val h = 0.52f * s
        canvas.drawPath(
            PaintKit.roundedRect(cx - h * 0.08f, cy - h * 0.5f, cx + h * 0.08f, cy + h * 0.25f, h * 0.08f),
            PaintKit.solid(grund),
        )
        canvas.drawCircle(cx, cy - h * 0.58f, h * 0.11f, PaintKit.line(grund, h * 0.07f))
        val zinkeL = Path().apply {
            moveTo(cx, cy + h * 0.18f)
            quadTo(cx - h * 0.34f, cy + h * 0.30f, cx - h * 0.30f, cy - h * 0.05f)
        }
        canvas.drawPath(zinkeL, PaintKit.line(grund, h * 0.13f))
        val zinkeR = Path().apply {
            moveTo(cx, cy + h * 0.18f)
            quadTo(cx + h * 0.34f, cy + h * 0.30f, cx + h * 0.30f, cy - h * 0.05f)
        }
        canvas.drawPath(zinkeR, PaintKit.line(grund, h * 0.13f))
        canvas.drawCircle(cx, cy + h * 0.28f, h * 0.09f, PaintKit.solid(grund))
    }

    /** Zahnrad mit acht Zähnen, Nabe und Lichtkante. */
    private fun zahnrad(canvas: Canvas, s: Float, grund: Int) {
        val c = 0.5f * s
        val koerper = PaintKit.solid(grund)
        for (i in 0 until 8) {
            val winkel = Math.toRadians(i * 45.0)
            val zx = c + (0.28f * s) * Math.cos(winkel).toFloat()
            val zy = c + (0.28f * s) * Math.sin(winkel).toFloat()
            canvas.drawCircle(zx, zy, 0.07f * s, koerper)
        }
        canvas.drawCircle(c, c, 0.24f * s, koerper)
        canvas.drawCircle(c, c, 0.09f * s, PaintKit.solid(KACHEL))
        // Lichtkante oben links
        canvas.drawArc(
            RectF(c - 0.17f * s, c - 0.17f * s, c + 0.17f * s, c + 0.17f * s),
            150f, 120f, false,
            PaintKit.line(PaintKit.withAlpha(PaintKit.WEISS, 120), 0.035f * s),
        )
    }

    /** Facettierter Kristall in Akzentfarbe mit Glanzpunkt. */
    private fun kristall(canvas: Canvas, s: Float, akzent: Int) {
        val stein = Path().apply {
            moveTo(0.50f * s, 0.24f * s)
            lineTo(0.66f * s, 0.42f * s)
            lineTo(0.60f * s, 0.74f * s)
            lineTo(0.40f * s, 0.74f * s)
            lineTo(0.34f * s, 0.42f * s)
            close()
        }
        canvas.drawPath(stein, PaintKit.solid(akzent))
        val facette = Path().apply {
            moveTo(0.50f * s, 0.24f * s)
            lineTo(0.66f * s, 0.42f * s)
            lineTo(0.50f * s, 0.74f * s)
            lineTo(0.50f * s, 0.42f * s)
            close()
        }
        canvas.drawPath(facette, PaintKit.solid(PaintKit.lighten(akzent, 45)))
        canvas.drawPath(stein, PaintKit.line(PaintKit.shade(akzent, 0.6f), 0.02f * s))
        canvas.drawCircle(0.44f * s, 0.38f * s, 0.025f * s, PaintKit.solid(PaintKit.withAlpha(PaintKit.WEISS, 220)))
    }

    /** Münze mit Prägerand, Stern-Prägung und Glanzbogen. */
    private fun muenze(canvas: Canvas, s: Float, grund: Int) {
        val c = 0.5f * s
        val cy = 0.53f * s
        canvas.drawCircle(c, cy, 0.21f * s, PaintKit.solid(grund))
        canvas.drawCircle(c, cy, 0.21f * s, PaintKit.line(PaintKit.shade(grund, 0.7f), 0.025f * s))
        canvas.drawCircle(c, cy, 0.15f * s, PaintKit.line(PaintKit.shade(grund, 0.78f), 0.018f * s))
        canvas.drawPath(PaintKit.diamond(c, cy, 0.07f * s, 0.09f * s), PaintKit.solid(PaintKit.shade(grund, 0.72f)))
        canvas.drawArc(
            RectF(c - 0.17f * s, cy - 0.17f * s, c + 0.17f * s, cy + 0.17f * s),
            170f, 90f, false,
            PaintKit.line(PaintKit.withAlpha(PaintKit.WEISS, 150), 0.03f * s),
        )
    }

    /** Amulett: Tropfenstein in Akzentfarbe an einem Lederband. */
    private fun amulett(canvas: Canvas, s: Float, grund: Int, akzent: Int) {
        val band = Path().apply {
            moveTo(0.40f * s, 0.34f * s)
            quadTo(0.5f * s, 0.18f * s, 0.60f * s, 0.34f * s)
        }
        canvas.drawPath(band, PaintKit.line(0xFF4A3B2A.toInt(), 0.035f * s))
        // Metallfassung
        canvas.drawCircle(0.5f * s, 0.36f * s, 0.045f * s, PaintKit.solid(grund))
        val tropfen = Path().apply {
            moveTo(0.5f * s, 0.36f * s)
            quadTo(0.68f * s, 0.52f * s, 0.5f * s, 0.74f * s)
            quadTo(0.32f * s, 0.52f * s, 0.5f * s, 0.36f * s)
            close()
        }
        canvas.drawPath(tropfen, PaintKit.solid(akzent))
        canvas.drawCircle(0.46f * s, 0.50f * s, 0.045f * s, PaintKit.solid(PaintKit.withAlpha(PaintKit.WEISS, 90)))
    }

    /** Fingerring mit gefasstem Akzent-Stein. */
    private fun ring(canvas: Canvas, s: Float, grund: Int, akzent: Int) {
        canvas.drawCircle(0.5f * s, 0.56f * s, 0.15f * s, PaintKit.line(grund, 0.055f * s))
        canvas.drawArc(
            RectF(0.35f * s, 0.41f * s, 0.65f * s, 0.71f * s),
            160f, 120f, false,
            PaintKit.line(PaintKit.lighten(grund, 45), 0.025f * s),
        )
        canvas.drawPath(PaintKit.diamond(0.5f * s, 0.36f * s, 0.07f * s, 0.08f * s), PaintKit.solid(akzent))
        canvas.drawCircle(0.48f * s, 0.34f * s, 0.018f * s, PaintKit.solid(PaintKit.withAlpha(PaintKit.WEISS, 220)))
    }

    /** Karte: Pergament mit Faltkanten, Routenpunkten und X-Marke. */
    private fun karte(canvas: Canvas, s: Float, grund: Int, akzent: Int) {
        canvas.drawPath(
            PaintKit.roundedRect(0.28f * s, 0.32f * s, 0.72f * s, 0.72f * s, 0.02f * s),
            PaintKit.solid(grund),
        )
        val falte = PaintKit.line(PaintKit.shade(grund, 0.85f), 0.015f * s)
        canvas.drawLine(0.425f * s, 0.32f * s, 0.425f * s, 0.72f * s, falte)
        canvas.drawLine(0.575f * s, 0.32f * s, 0.575f * s, 0.72f * s, falte)
        // Gepunktete Route zur X-Marke
        val punkt = PaintKit.solid(akzent)
        canvas.drawCircle(0.34f * s, 0.64f * s, 0.016f * s, punkt)
        canvas.drawCircle(0.42f * s, 0.56f * s, 0.016f * s, punkt)
        canvas.drawCircle(0.50f * s, 0.50f * s, 0.016f * s, punkt)
        canvas.drawCircle(0.56f * s, 0.44f * s, 0.016f * s, punkt)
        val x = PaintKit.line(CHILI, 0.03f * s)
        canvas.drawLine(0.60f * s, 0.38f * s, 0.66f * s, 0.44f * s, x)
        canvas.drawLine(0.66f * s, 0.38f * s, 0.60f * s, 0.44f * s, x)
    }

    /** Kompass: Gehäuse, helles Zifferblatt, rote Nord-Nadel. */
    private fun kompass(canvas: Canvas, s: Float, grund: Int) {
        val c = 0.5f * s
        val cy = 0.54f * s
        canvas.drawCircle(c, cy, 0.22f * s, PaintKit.solid(grund))
        canvas.drawCircle(c, cy, 0.17f * s, PaintKit.solid(0xFFE8E0CC.toInt()))
        // Nadel: rot nach Nord, dunkel nach Süd
        val nord = Path().apply {
            moveTo(c, cy - 0.13f * s)
            lineTo(c + 0.04f * s, cy)
            lineTo(c - 0.04f * s, cy)
            close()
        }
        canvas.drawPath(nord, PaintKit.solid(CHILI))
        val sued = Path().apply {
            moveTo(c, cy + 0.13f * s)
            lineTo(c + 0.04f * s, cy)
            lineTo(c - 0.04f * s, cy)
            close()
        }
        canvas.drawPath(sued, PaintKit.solid(0xFF46525E.toInt()))
        canvas.drawCircle(c, cy, 0.025f * s, PaintKit.solid(PaintKit.shade(grund, 0.7f)))
        // Krone/Öse oben
        canvas.drawCircle(c, cy - 0.26f * s, 0.04f * s, PaintKit.line(grund, 0.03f * s))
    }

    /** Gebogenes Signalhorn mit Querrillen und Mundstück. */
    private fun horn(canvas: Canvas, s: Float, grund: Int) {
        val bogen1 = Path().apply {
            moveTo(0.32f * s, 0.30f * s)
            quadTo(0.26f * s, 0.58f * s, 0.50f * s, 0.68f * s)
        }
        canvas.drawPath(bogen1, PaintKit.line(grund, 0.11f * s))
        val bogen2 = Path().apply {
            moveTo(0.50f * s, 0.68f * s)
            quadTo(0.68f * s, 0.74f * s, 0.74f * s, 0.60f * s)
        }
        canvas.drawPath(bogen2, PaintKit.line(grund, 0.16f * s))
        // Mundstück und Schallöffnung
        canvas.drawCircle(0.32f * s, 0.28f * s, 0.045f * s, PaintKit.solid(PaintKit.shade(grund, 0.75f)))
        canvas.drawOval(RectF(0.68f * s, 0.50f * s, 0.82f * s, 0.66f * s), PaintKit.solid(PaintKit.shade(grund, 0.68f)))
        // Querrillen
        val rille = PaintKit.line(PaintKit.shade(grund, 0.7f), 0.02f * s)
        canvas.drawLine(0.30f * s, 0.44f * s, 0.38f * s, 0.46f * s, rille)
        canvas.drawLine(0.36f * s, 0.58f * s, 0.44f * s, 0.62f * s, rille)
    }

    /** Schreibfeder in Akzentfarbe mit Kiel und Tintentropfen. */
    private fun feder(canvas: Canvas, s: Float, akzent: Int) {
        val fahne = Path().apply {
            moveTo(0.62f * s, 0.24f * s)
            quadTo(0.74f * s, 0.40f * s, 0.52f * s, 0.60f * s)
            quadTo(0.44f * s, 0.50f * s, 0.62f * s, 0.24f * s)
            close()
        }
        canvas.drawPath(fahne, PaintKit.solid(akzent))
        val fahne2 = Path().apply {
            moveTo(0.62f * s, 0.24f * s)
            quadTo(0.48f * s, 0.42f * s, 0.52f * s, 0.60f * s)
            quadTo(0.40f * s, 0.52f * s, 0.62f * s, 0.24f * s)
            close()
        }
        canvas.drawPath(fahne2, PaintKit.solid(PaintKit.lighten(akzent, 40)))
        // Kiel bis zur Spitze
        canvas.drawLine(
            0.52f * s, 0.60f * s, 0.36f * s, 0.76f * s,
            PaintKit.line(0xFFE8E0CC.toInt(), 0.03f * s),
        )
        canvas.drawCircle(0.33f * s, 0.79f * s, 0.035f * s, PaintKit.solid(TINTE))
    }

    /** Kräuterbund: drei Stängel mit Blättern und Akzent-Bindeband. */
    private fun kraut(canvas: Canvas, s: Float, akzent: Int) {
        val stiel = PaintKit.line(GRUEN, 0.03f * s)
        val mitte = Path().apply {
            moveTo(0.50f * s, 0.74f * s)
            quadTo(0.50f * s, 0.46f * s, 0.50f * s, 0.30f * s)
        }
        canvas.drawPath(mitte, stiel)
        val links = Path().apply {
            moveTo(0.50f * s, 0.72f * s)
            quadTo(0.38f * s, 0.56f * s, 0.34f * s, 0.38f * s)
        }
        canvas.drawPath(links, stiel)
        val rechts = Path().apply {
            moveTo(0.50f * s, 0.72f * s)
            quadTo(0.62f * s, 0.56f * s, 0.66f * s, 0.38f * s)
        }
        canvas.drawPath(rechts, stiel)
        // Blätter an den Stängelenden
        val blatt = PaintKit.solid(PaintKit.lighten(GRUEN, 40))
        canvas.drawOval(RectF(0.44f * s, 0.22f * s, 0.56f * s, 0.36f * s), blatt)
        canvas.drawOval(RectF(0.28f * s, 0.32f * s, 0.40f * s, 0.44f * s), blatt)
        canvas.drawOval(RectF(0.60f * s, 0.32f * s, 0.72f * s, 0.44f * s), blatt)
        // Bindeband
        canvas.drawRect(0.45f * s, 0.66f * s, 0.55f * s, 0.71f * s, PaintKit.solid(akzent))
    }

    /** Pilz: heller Stiel, Akzent-Kappe mit Tupfen. */
    private fun pilz(canvas: Canvas, s: Float, akzent: Int) {
        canvas.drawPath(
            PaintKit.roundedRect(0.44f * s, 0.50f * s, 0.56f * s, 0.76f * s, 0.05f * s),
            PaintKit.solid(0xFFE8E0CC.toInt()),
        )
        val kappe = Path().apply {
            moveTo(0.28f * s, 0.52f * s)
            quadTo(0.30f * s, 0.26f * s, 0.50f * s, 0.24f * s)
            quadTo(0.70f * s, 0.26f * s, 0.72f * s, 0.52f * s)
            close()
        }
        canvas.drawPath(kappe, PaintKit.solid(akzent))
        canvas.drawLine(0.30f * s, 0.52f * s, 0.70f * s, 0.52f * s, PaintKit.line(PaintKit.shade(akzent, 0.65f), 0.025f * s))
        val tupfen = PaintKit.solid(PaintKit.withAlpha(PaintKit.WEISS, 210))
        canvas.drawCircle(0.40f * s, 0.38f * s, 0.035f * s, tupfen)
        canvas.drawCircle(0.56f * s, 0.32f * s, 0.03f * s, tupfen)
        canvas.drawCircle(0.60f * s, 0.44f * s, 0.028f * s, tupfen)
    }

    /** Diagonaler Knochen mit Gelenkknubbeln. */
    private fun knochen(canvas: Canvas, s: Float, grund: Int) {
        canvas.save()
        canvas.rotate(-30f, 0.5f * s, 0.5f * s)
        canvas.drawPath(
            PaintKit.roundedRect(0.32f * s, 0.465f * s, 0.68f * s, 0.535f * s, 0.03f * s),
            PaintKit.solid(grund),
        )
        val knubbel = PaintKit.solid(grund)
        canvas.drawCircle(0.31f * s, 0.455f * s, 0.055f * s, knubbel)
        canvas.drawCircle(0.31f * s, 0.545f * s, 0.055f * s, knubbel)
        canvas.drawCircle(0.69f * s, 0.455f * s, 0.055f * s, knubbel)
        canvas.drawCircle(0.69f * s, 0.545f * s, 0.055f * s, knubbel)
        canvas.drawLine(
            0.36f * s, 0.48f * s, 0.62f * s, 0.48f * s,
            PaintKit.line(PaintKit.withAlpha(PaintKit.WEISS, 130), 0.02f * s),
        )
        canvas.restore()
    }

    /** Zugeschnürter Beutel mit Akzent-Flicken. */
    private fun beutel(canvas: Canvas, s: Float, grund: Int, akzent: Int) {
        val korpus = Path().apply {
            moveTo(0.38f * s, 0.42f * s)
            quadTo(0.26f * s, 0.70f * s, 0.50f * s, 0.76f * s)
            quadTo(0.74f * s, 0.70f * s, 0.62f * s, 0.42f * s)
            close()
        }
        canvas.drawPath(korpus, PaintKit.solid(grund))
        // Zusammengeraffter Hals mit Schnur
        canvas.drawPath(
            PaintKit.roundedRect(0.43f * s, 0.32f * s, 0.57f * s, 0.44f * s, 0.03f * s),
            PaintKit.solid(PaintKit.shade(grund, 0.85f)),
        )
        canvas.drawLine(0.41f * s, 0.42f * s, 0.59f * s, 0.42f * s, PaintKit.line(TAU, 0.035f * s))
        // Flicken mit Nähten
        canvas.drawPath(
            PaintKit.roundedRect(0.46f * s, 0.56f * s, 0.58f * s, 0.66f * s, 0.02f * s),
            PaintKit.solid(akzent),
        )
        val naht = PaintKit.line(PaintKit.shade(akzent, 0.6f), 0.015f * s)
        canvas.drawLine(0.48f * s, 0.58f * s, 0.50f * s, 0.60f * s, naht)
        canvas.drawLine(0.54f * s, 0.62f * s, 0.56f * s, 0.64f * s, naht)
    }

    /** Krug mit Henkel; die Füllung trägt die Akzentfarbe. */
    private fun krug(canvas: Canvas, s: Float, grund: Int, akzent: Int) {
        canvas.drawPath(
            PaintKit.roundedRect(0.34f * s, 0.36f * s, 0.62f * s, 0.76f * s, 0.05f * s),
            PaintKit.solid(grund),
        )
        canvas.drawRect(0.37f * s, 0.38f * s, 0.42f * s, 0.74f * s, PaintKit.solid(PaintKit.lighten(grund, 30)))
        val henkel = Path().apply {
            moveTo(0.62f * s, 0.44f * s)
            quadTo(0.76f * s, 0.48f * s, 0.72f * s, 0.58f * s)
            quadTo(0.69f * s, 0.65f * s, 0.62f * s, 0.64f * s)
        }
        canvas.drawPath(henkel, PaintKit.line(PaintKit.shade(grund, 0.7f), 0.05f * s))
        canvas.drawOval(RectF(0.36f * s, 0.32f * s, 0.60f * s, 0.42f * s), PaintKit.solid(akzent))
    }

    /** Handspiegel mit Griff, Glasfläche und Lichtreflex. */
    private fun spiegel(canvas: Canvas, s: Float, grund: Int) {
        canvas.drawPath(
            PaintKit.roundedRect(0.46f * s, 0.58f * s, 0.54f * s, 0.82f * s, 0.04f * s),
            PaintKit.solid(grund),
        )
        canvas.drawCircle(0.5f * s, 0.42f * s, 0.21f * s, PaintKit.solid(grund))
        canvas.drawCircle(0.5f * s, 0.42f * s, 0.16f * s, PaintKit.solid(GLAS_BLAU))
        canvas.drawLine(
            0.42f * s, 0.48f * s, 0.55f * s, 0.33f * s,
            PaintKit.line(PaintKit.withAlpha(PaintKit.WEISS, 200), 0.035f * s),
        )
    }

    /** Glocke mit Krone, Schlagring und Klöppel. */
    private fun glocke(canvas: Canvas, s: Float, grund: Int) {
        canvas.drawCircle(0.5f * s, 0.26f * s, 0.04f * s, PaintKit.line(PaintKit.shade(grund, 0.75f), 0.03f * s))
        val korpus = Path().apply {
            moveTo(0.36f * s, 0.62f * s)
            quadTo(0.37f * s, 0.34f * s, 0.50f * s, 0.30f * s)
            quadTo(0.63f * s, 0.34f * s, 0.64f * s, 0.62f * s)
            close()
        }
        canvas.drawPath(korpus, PaintKit.solid(grund))
        canvas.drawPath(
            PaintKit.roundedRect(0.32f * s, 0.62f * s, 0.68f * s, 0.68f * s, 0.02f * s),
            PaintKit.solid(PaintKit.shade(grund, 0.78f)),
        )
        canvas.drawCircle(0.5f * s, 0.73f * s, 0.045f * s, PaintKit.solid(PaintKit.shade(grund, 0.6f)))
        // Lichtkante links
        val glanz = Path().apply {
            moveTo(0.41f * s, 0.58f * s)
            quadTo(0.41f * s, 0.38f * s, 0.48f * s, 0.33f * s)
        }
        canvas.drawPath(glanz, PaintKit.line(PaintKit.withAlpha(PaintKit.WEISS, 130), 0.025f * s))
    }

    /** Kantiger Felsbrocken mit dunkler Facette und Splitterkanten. */
    private fun stein(canvas: Canvas, s: Float, grund: Int) {
        val brocken = Path().apply {
            moveTo(0.36f * s, 0.36f * s)
            lineTo(0.60f * s, 0.30f * s)
            lineTo(0.72f * s, 0.52f * s)
            lineTo(0.56f * s, 0.70f * s)
            lineTo(0.34f * s, 0.62f * s)
            close()
        }
        canvas.drawPath(brocken, PaintKit.solid(grund))
        val facette = Path().apply {
            moveTo(0.60f * s, 0.30f * s)
            lineTo(0.72f * s, 0.52f * s)
            lineTo(0.56f * s, 0.70f * s)
            lineTo(0.52f * s, 0.48f * s)
            close()
        }
        canvas.drawPath(facette, PaintKit.solid(PaintKit.shade(grund, 0.78f)))
        val kante = PaintKit.line(PaintKit.lighten(grund, 35), 0.02f * s)
        canvas.drawLine(0.36f * s, 0.36f * s, 0.52f * s, 0.48f * s, kante)
        canvas.drawLine(0.40f * s, 0.58f * s, 0.50f * s, 0.62f * s, kante)
    }

    /** Spielwürfel als Iso-Quader mit Augen auf zwei Flächen. */
    private fun wuerfel(canvas: Canvas, s: Float, grund: Int, akzent: Int) {
        val c = 0.5f * s
        val cy = 0.52f * s
        val r = 0.20f * s
        canvas.drawPath(PaintKit.diamond(c, cy - r * 0.55f, r, r * 0.55f), PaintKit.solid(PaintKit.lighten(grund, 25)))
        canvas.drawRect(c - r, cy - r * 0.55f, c, cy + r * 0.75f, PaintKit.solid(PaintKit.shade(grund, 0.8f)))
        canvas.drawRect(c, cy - r * 0.55f, c + r, cy + r * 0.75f, PaintKit.solid(PaintKit.shade(grund, 0.92f)))
        val auge = PaintKit.solid(PaintKit.shade(akzent, 0.75f))
        // Eins oben, zwei links, drei rechts
        canvas.drawCircle(c, cy - r * 0.55f, 0.028f * s, auge)
        canvas.drawCircle(c - r * 0.6f, cy - r * 0.1f, 0.026f * s, auge)
        canvas.drawCircle(c - r * 0.4f, cy + r * 0.4f, 0.026f * s, auge)
        canvas.drawCircle(c + r * 0.4f, cy - r * 0.1f, 0.026f * s, auge)
        canvas.drawCircle(c + r * 0.6f, cy + r * 0.15f, 0.026f * s, auge)
        canvas.drawCircle(c + r * 0.25f, cy + r * 0.4f, 0.026f * s, auge)
    }

    /** Zweifarbige Flamme (außen orange, Kern gelb). */
    private fun flamme(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        val aussen = Path().apply {
            moveTo(cx, cy - r * 1.2f)
            quadTo(cx + r, cy - r * 0.1f, cx, cy + r * 0.8f)
            quadTo(cx - r, cy - r * 0.1f, cx, cy - r * 1.2f)
            close()
        }
        canvas.drawPath(aussen, PaintKit.solid(FLAMME_A))
        val innen = Path().apply {
            moveTo(cx, cy - r * 0.5f)
            quadTo(cx + r * 0.45f, cy + r * 0.1f, cx, cy + r * 0.6f)
            quadTo(cx - r * 0.45f, cy + r * 0.1f, cx, cy - r * 0.5f)
            close()
        }
        canvas.drawPath(innen, PaintKit.solid(FLAMME_B))
    }
}
