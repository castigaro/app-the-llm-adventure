package com.castigaro.thellmadventure.render

import android.graphics.Canvas
import android.graphics.Path
import com.castigaro.thellmadventure.world.Building
import com.castigaro.thellmadventure.world.BuildingStyle
import com.castigaro.thellmadventure.world.Limits
import com.castigaro.thellmadventure.world.OutdoorMap
import com.castigaro.thellmadventure.world.Parcel
import com.castigaro.thellmadventure.world.SLOT_COLORS
import com.castigaro.thellmadventure.world.Terrain
import kotlin.math.sin

/**
 * Malt die Außenkarte in Weltkoordinaten: Geländekacheln (Detail im
 * [TerrainPainter]), Nebelränder mit „?"-Schimmer (dahinter entsteht die Welt
 * erst — dasselbe Versprechen wie unentdeckte Türen) und Gebäude als
 * Iso-Körper mit Dach, Fenster und Türstufe. Draußen gibt es keine Wände —
 * die Karte läuft in den Nebel aus.
 *
 * Die Canvas ist beim Aufruf bereits kamera-transformiert (IsoView). Das
 * statische Gelände nimmt die IsoView je Parzelle in ein Picture auf
 * ([drawParcelStatic]); animiert bleiben nur Wasserglanz und Nebelschimmer.
 */
object MapRenderer {

    private val TW = IsoMath.TILE_W
    private val TH = IsoMath.TILE_H

    private const val NEBEL = 0xFF10141C.toInt()
    private const val DACH = 0xFF8A4A3A.toInt()
    private const val FENSTER = 0xFFE8D2A0.toInt()
    private const val STUFE = 0xFF8E8E96.toInt()

    /** Statisches Gelände EINER Parzelle — für die Picture-Aufnahme der IsoView. */
    fun drawParcelStatic(canvas: Canvas, map: OutdoorMap, parcel: Parcel) {
        val ox = parcel.originTx()
        val oy = parcel.originTy()
        for (ly in 0 until Limits.PARZELLE) {
            for (lx in 0 until Limits.PARZELLE) {
                TerrainPainter.drawStatic(
                    canvas, parcel.terrainLocal(lx, ly),
                    ox + lx, oy + ly, map::terrainAt,
                )
            }
        }
    }

    /** Animierte Wasser-Glanzbänder im Sichtbereich; true, wenn Wasser sichtbar ist. */
    fun drawWasserGlanz(canvas: Canvas, map: OutdoorMap, range: IntArray, timeMs: Long): Boolean {
        var sichtbar = false
        for (ty in range[1] until range[3]) {
            for (tx in range[0] until range[2]) {
                if (map.terrainAt(tx, ty) != Terrain.WASSER) continue
                sichtbar = true
                TerrainPainter.drawWasserGlanz(canvas, tx, ty, timeMs)
            }
        }
        return sichtbar
    }

    /**
     * Nebel: Kacheln ohne Parzelle, die an aufgedecktes Land grenzen, werden
     * als dunkler Saum gemalt; begehbare Randkacheln davor schimmern mit „?".
     * true, wenn ein Schimmer sichtbar ist (hält den Ambient-Takt wach).
     */
    fun drawFog(canvas: Canvas, map: OutdoorMap, range: IntArray, timeMs: Long): Boolean {
        for (ty in range[1] until range[3]) {
            for (tx in range[0] until range[2]) {
                if (map.parcelForTile(tx, ty) != null) continue
                val amLand = map.parcelForTile(tx - 1, ty) != null ||
                    map.parcelForTile(tx + 1, ty) != null ||
                    map.parcelForTile(tx, ty - 1) != null ||
                    map.parcelForTile(tx, ty + 1) != null
                if (!amLand) continue
                val cx = IsoMath.tileCenterX(tx, ty)
                val cy = IsoMath.tileCenterY(tx, ty)
                canvas.drawPath(
                    PaintKit.diamond(cx, cy, TW / 2f, TH / 2f),
                    PaintKit.solid(PaintKit.withAlpha(NEBEL, 210)),
                )
            }
        }
        // „?"-Schimmer auf den begehbaren Torkacheln vor dem Nebel
        var schimmer = false
        for (ty in range[1] until range[3]) {
            for (tx in range[0] until range[2]) {
                if (map.frontierAt(tx, ty) == null) continue
                schimmer = true
                val alpha = (140 + 100 * sin(timeMs / 400.0 + (tx * 7 + ty * 13) * 0.5)).toInt().coerceIn(40, 240)
                val q = PaintKit.text
                q.textSize = TH * 0.55f
                q.color = PaintKit.withAlpha(PaintKit.WEISS, alpha)
                canvas.drawText(
                    "?",
                    IsoMath.tileCenterX(tx, ty),
                    IsoMath.tileCenterY(tx, ty) + TH * 0.15f,
                    q,
                )
                q.color = PaintKit.WEISS
            }
        }
        return schimmer
    }

    /** Wandhöhe eines Gebäudestils in Weltpixeln. */
    private fun wandHoehe(stil: BuildingStyle): Float = when (stil) {
        BuildingStyle.HUETTE -> TH * 1.2f
        BuildingStyle.HAUS -> TH * 1.6f
        BuildingStyle.TURM -> TH * 2.6f
        BuildingStyle.SCHEUNE -> TH * 1.4f
    }

    private fun dachHoehe(stil: BuildingStyle): Float = when (stil) {
        BuildingStyle.HUETTE -> TH * 0.7f
        BuildingStyle.HAUS -> TH * 0.9f
        BuildingStyle.TURM -> 0f
        BuildingStyle.SCHEUNE -> TH * 0.8f
    }

    /** Gesamthöhe (für Label-Position und Treffer-Test). */
    fun gesamtHoehe(stil: BuildingStyle): Float = wandHoehe(stil) + dachHoehe(stil)

    /**
     * Ein Gebäude als Iso-Körper: Süd- und Ostfassade mit Plankenlinien,
     * Dachprisma mit Schindeln (Türme: Zinnendeckel), Fenster, Türnische und
     * Türstufe. Wird über die Entity-Liste der IsoView tiefen-sortiert.
     */
    fun drawBuilding(canvas: Canvas, b: Building, timeMs: Long) {
        val base = SLOT_COLORS[b.colorSlot.coerceIn(0, SLOT_COLORS.size - 1)]
        val hoehe = wandHoehe(b.stil)
        val dach = dachHoehe(b.stil)

        val tx = b.tx.toFloat()
        val ty = b.ty.toFloat()
        val w = b.w.toFloat()
        val h = b.h.toFloat()

        // Bodenecken der Grundfläche
        val topX = IsoMath.worldX(tx, ty); val topY = IsoMath.worldY(tx, ty)
        val rightX = IsoMath.worldX(tx + w, ty); val rightY = IsoMath.worldY(tx + w, ty)
        val leftX = IsoMath.worldX(tx, ty + h); val leftY = IsoMath.worldY(tx, ty + h)
        val bottomX = IsoMath.worldX(tx + w, ty + h); val bottomY = IsoMath.worldY(tx + w, ty + h)

        // Türstufe VOR der Südfront (unter dem späteren Spieler-Sprite)
        val dt = b.doorTile()
        canvas.drawPath(
            PaintKit.diamond(IsoMath.tileCenterX(dt.first, dt.second), IsoMath.tileCenterY(dt.first, dt.second), TW * 0.26f, TH * 0.26f),
            PaintKit.solid(PaintKit.withAlpha(STUFE, 160)),
        )

        // Südfassade (vorn links, heller) und Ostfassade (vorn rechts, dunkler)
        val sued = Path().apply {
            moveTo(leftX, leftY - hoehe); lineTo(bottomX, bottomY - hoehe)
            lineTo(bottomX, bottomY); lineTo(leftX, leftY); close()
        }
        canvas.drawPath(sued, PaintKit.solid(PaintKit.shade(base, 0.92f)))
        val ost = Path().apply {
            moveTo(bottomX, bottomY - hoehe); lineTo(rightX, rightY - hoehe)
            lineTo(rightX, rightY); lineTo(bottomX, bottomY); close()
        }
        canvas.drawPath(ost, PaintKit.solid(PaintKit.shade(base, 0.68f)))

        // Plankenlinien auf beiden Fassaden
        val plankeSued = PaintKit.line(PaintKit.shade(base, 0.8f), TH * 0.025f)
        val plankeOst = PaintKit.line(PaintKit.shade(base, 0.58f), TH * 0.025f)
        floatArrayOf(0.33f, 0.66f).forEach { f ->
            canvas.drawLine(leftX, leftY - hoehe * f, bottomX, bottomY - hoehe * f, plankeSued)
            canvas.drawLine(bottomX, bottomY - hoehe * f, rightX, rightY - hoehe * f, plankeOst)
        }

        // Fenster auf der Südfassade (erste Grundriss-Spalte, nicht die Türspalte)
        val fu = 0.5f // Fenstermitte in Kacheln ab tx
        fun sx(u: Float, v: Float) = IsoMath.worldX(tx + u, ty + h)
        fun sy(u: Float, v: Float) = IsoMath.worldY(tx + u, ty + h) - hoehe * v
        val fenster = Path().apply {
            moveTo(sx(fu - 0.22f, 0.48f), sy(fu - 0.22f, 0.48f))
            lineTo(sx(fu + 0.22f, 0.48f), sy(fu + 0.22f, 0.48f))
            lineTo(sx(fu + 0.22f, 0.78f), sy(fu + 0.22f, 0.78f))
            lineTo(sx(fu - 0.22f, 0.78f), sy(fu - 0.22f, 0.78f))
            close()
        }
        canvas.drawPath(fenster, PaintKit.solid(PaintKit.withAlpha(FENSTER, 210)))
        canvas.drawPath(fenster, PaintKit.line(PaintKit.shade(base, 0.5f), TH * 0.03f))
        canvas.drawLine(sx(fu, 0.48f), sy(fu, 0.48f), sx(fu, 0.78f), sy(fu, 0.78f), PaintKit.line(PaintKit.shade(base, 0.5f), TH * 0.02f))

        if (dach <= 0f) {
            // Turm: flacher Deckel mit Zinnenkante
            val deckel = Path().apply {
                moveTo(topX, topY - hoehe); lineTo(rightX, rightY - hoehe)
                lineTo(bottomX, bottomY - hoehe); lineTo(leftX, leftY - hoehe); close()
            }
            canvas.drawPath(deckel, PaintKit.solid(PaintKit.shade(base, 1.15f)))
            canvas.drawPath(deckel, PaintKit.line(PaintKit.shade(base, 0.5f), TH * 0.06f))
            // Zinnen entlang der vorderen Deckelkanten
            val zinne = PaintKit.solid(PaintKit.shade(base, 1.0f))
            floatArrayOf(0.2f, 0.5f, 0.8f).forEach { f ->
                val zx = leftX + (bottomX - leftX) * f
                val zy = (leftY + (bottomY - leftY) * f) - hoehe
                canvas.drawRect(zx - TH * 0.07f, zy - TH * 0.18f, zx + TH * 0.07f, zy, zinne)
            }
        } else {
            // Dachprisma: First läuft mittig in x-Richtung
            val ridgeLx = IsoMath.worldX(tx, ty + h / 2f)
            val ridgeLy = IsoMath.worldY(tx, ty + h / 2f) - hoehe - dach
            val ridgeRx = IsoMath.worldX(tx + w, ty + h / 2f)
            val ridgeRy = IsoMath.worldY(tx + w, ty + h / 2f) - hoehe - dach
            val dachHinten = Path().apply {
                moveTo(topX, topY - hoehe); lineTo(rightX, rightY - hoehe)
                lineTo(ridgeRx, ridgeRy); lineTo(ridgeLx, ridgeLy); close()
            }
            canvas.drawPath(dachHinten, PaintKit.solid(PaintKit.shade(DACH, 0.7f)))
            val dachVorn = Path().apply {
                moveTo(leftX, leftY - hoehe); lineTo(bottomX, bottomY - hoehe)
                lineTo(ridgeRx, ridgeRy); lineTo(ridgeLx, ridgeLy); close()
            }
            canvas.drawPath(dachVorn, PaintKit.solid(DACH))
            // Schindellinien parallel zur Traufe
            val schindel = PaintKit.line(PaintKit.shade(DACH, 0.8f), TH * 0.03f)
            floatArrayOf(0.35f, 0.7f).forEach { f ->
                canvas.drawLine(
                    leftX + (ridgeLx - leftX) * f, (leftY - hoehe) + (ridgeLy - (leftY - hoehe)) * f,
                    bottomX + (ridgeRx - bottomX) * f, (bottomY - hoehe) + (ridgeRy - (bottomY - hoehe)) * f,
                    schindel,
                )
            }
            canvas.drawLine(ridgeLx, ridgeLy, ridgeRx, ridgeRy, PaintKit.line(PaintKit.shade(DACH, 1.3f), TH * 0.06f))
        }

        // Türnische mittig an der Südfront (über der Eingangskachel)
        val doorMid = b.tx + b.w / 2 + 0.5f
        val d0x = IsoMath.worldX(doorMid - 0.3f, ty + h); val d0y = IsoMath.worldY(doorMid - 0.3f, ty + h)
        val d1x = IsoMath.worldX(doorMid + 0.3f, ty + h); val d1y = IsoMath.worldY(doorMid + 0.3f, ty + h)
        val tuer = Path().apply {
            moveTo(d0x, d0y); lineTo(d1x, d1y)
            lineTo(d1x, d1y - TH * 1.0f); lineTo(d0x, d0y - TH * 1.0f); close()
        }
        canvas.drawPath(tuer, PaintKit.solid(PaintKit.shade(base, 0.35f)))
        // Türsturz
        canvas.drawLine(d0x, d0y - TH * 1.0f, d1x, d1y - TH * 1.0f, PaintKit.line(PaintKit.shade(base, 1.2f), TH * 0.04f))

        // Kantenlinien heben den Körper vom Gelände ab
        val kante = PaintKit.line(PaintKit.shade(base, 0.5f), TH * 0.05f)
        canvas.drawLine(leftX, leftY, leftX, leftY - hoehe, kante)
        canvas.drawLine(bottomX, bottomY, bottomX, bottomY - hoehe, kante)
        canvas.drawLine(rightX, rightY, rightX, rightY - hoehe, kante)
    }

    /**
     * Gebäudenamen samt „?"-Schimmer (Inneres unentdeckt) und Schloss-Punkt —
     * NACH den Entities, damit nichts die Beschriftung verdeckt. true, wenn
     * ein Schimmer sichtbar ist.
     */
    fun drawBuildingLabels(canvas: Canvas, map: OutdoorMap, range: IntArray, timeMs: Long, labelScale: Float): Boolean {
        var schimmer = false
        map.buildings.values.toList().forEach { b ->
            if (b.tx + b.w < range[0] || b.tx > range[2] || b.ty + b.h < range[1] || b.ty > range[3]) return@forEach
            val cx = IsoMath.worldX(b.tx + b.w / 2f, b.ty + b.h / 2f)
            val cy = IsoMath.worldY(b.tx + b.w / 2f, b.ty + b.h / 2f) - gesamtHoehe(b.stil)

            if (b.targetRoomId == null) {
                schimmer = true
                val alpha = (140 + 100 * sin(timeMs / 400.0)).toInt().coerceIn(40, 240)
                val q = PaintKit.text
                q.textSize = TH * 0.55f
                q.color = PaintKit.withAlpha(PaintKit.WEISS, alpha)
                canvas.drawText("?", cx, cy - TH * 0.15f, q)
                q.color = PaintKit.WEISS
            }
            if (b.locked) {
                canvas.drawCircle(cx + TW * 0.18f, cy - TH * 0.3f, TH * 0.09f, PaintKit.solid(PaintKit.GOLD))
            }
            val label = PaintKit.text
            label.textSize = TH * 0.16f * labelScale
            label.color = PaintKit.withAlpha(PaintKit.WEISS, 220)
            label.setShadowLayer(label.textSize * 0.18f, 0f, 0f, 0xCC000000.toInt())
            canvas.drawText(b.name, cx, cy - TH * 0.75f, label)
            label.clearShadowLayer()
            label.color = PaintKit.WEISS
        }
        return schimmer
    }
}
