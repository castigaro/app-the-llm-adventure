package com.castigaro.thellmadventure.render

import android.graphics.Canvas
import android.graphics.Path
import com.castigaro.thellmadventure.world.Door
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.Wall
import kotlin.math.sin

/**
 * Malt einen Raum in Weltkoordinaten: Bodenrauten, Nord-/Westwände mit
 * Türöffnungen und Tür-Markierungen. Unentdeckte Türen (Ziel noch nicht
 * generiert) schimmern mit einem „?" — dahinter entsteht die Welt erst.
 *
 * Die Canvas ist beim Aufruf bereits kamera-transformiert (IsoView).
 */
object RoomRenderer {

    private val TW = IsoMath.TILE_W
    private val TH = IsoMath.TILE_H
    private val WALL_H = IsoMath.WALL_H

    fun drawFloor(canvas: Canvas, room: Room) {
        val pal = room.palette
        for (ty in 0 until room.h) {
            for (tx in 0 until room.w) {
                val cx = IsoMath.tileCenterX(tx, ty)
                val cy = IsoMath.tileCenterY(tx, ty)
                val base = if ((tx + ty) % 2 == 0) pal.floorA else pal.floorB
                canvas.drawPath(PaintKit.diamond(cx, cy, TW / 2f, TH / 2f), PaintKit.solid(base))
                // Dielen-/Fliesenlinie entlang einer Rautenkante — dezente Textur
                canvas.drawLine(
                    cx - TW * 0.5f, cy, cx, cy - TH * 0.5f,
                    PaintKit.line(PaintKit.shade(base, 0.93f), TH * 0.025f),
                )
                // Dezente, deterministische Akzent-Sprenkel
                if ((tx * 31 + ty * 17) % 7 == 0) {
                    canvas.drawPath(
                        PaintKit.diamond(cx, cy, TW * 0.12f, TH * 0.12f),
                        PaintKit.solid(PaintKit.shade(base, 0.9f)),
                    )
                }
            }
        }
        // Kantenlinie um den Boden
        val edge = PaintKit.line(PaintKit.shade(pal.floorB, 0.6f), TH * 0.06f)
        canvas.drawPath(floorOutline(room), edge)
    }

    private fun floorOutline(room: Room): Path = Path().apply {
        val w = room.w.toFloat()
        val h = room.h.toFloat()
        moveTo(IsoMath.worldX(0f, 0f), IsoMath.worldY(0f, 0f))
        lineTo(IsoMath.worldX(w, 0f), IsoMath.worldY(w, 0f))
        lineTo(IsoMath.worldX(w, h), IsoMath.worldY(w, h))
        lineTo(IsoMath.worldX(0f, h), IsoMath.worldY(0f, h))
        close()
    }

    /** Nord- und Westwand mit Türöffnungen. */
    fun drawWalls(canvas: Canvas, room: Room, timeMs: Long) {
        val pal = room.palette
        for (tx in 0 until room.w) {
            val door = room.doors.firstOrNull { it.wall == Wall.N && it.pos == tx }
            wallSegment(
                canvas,
                IsoMath.worldX(tx.toFloat(), 0f), IsoMath.worldY(tx.toFloat(), 0f),
                IsoMath.worldX(tx + 1f, 0f), IsoMath.worldY(tx + 1f, 0f),
                pal.wallLight, door,
            )
        }
        for (ty in 0 until room.h) {
            val door = room.doors.firstOrNull { it.wall == Wall.W && it.pos == ty }
            wallSegment(
                canvas,
                IsoMath.worldX(0f, ty.toFloat()), IsoMath.worldY(0f, ty.toFloat()),
                IsoMath.worldX(0f, ty + 1f), IsoMath.worldY(0f, ty + 1f),
                pal.wallDark, door,
            )
        }
    }

    private fun wallSegment(
        canvas: Canvas,
        ax: Float, ay: Float, bx: Float, by: Float,
        color: Int, door: Door?,
    ) {
        val quad = Path().apply {
            moveTo(ax, ay - WALL_H)
            lineTo(bx, by - WALL_H)
            lineTo(bx, by)
            lineTo(ax, ay)
            close()
        }
        canvas.drawPath(quad, PaintKit.solid(color))
        // Obere Kante
        canvas.drawLine(ax, ay - WALL_H, bx, by - WALL_H, PaintKit.line(PaintKit.shade(color, 1.25f), TH * 0.07f))

        if (door != null) {
            // Türnische: dunkler Bogen in der Wandfläche
            val mx = (ax + bx) / 2f
            val my = (ay + by) / 2f
            val recess = Path().apply {
                moveTo(lerp(ax, mx, 0.45f), lerp(ay, my, 0.45f))
                lineTo(lerp(ax, mx, 0.45f), lerp(ay, my, 0.45f) - WALL_H * 0.72f)
                quadTo(mx, my - WALL_H * 0.98f, lerp(bx, mx, 0.45f), lerp(by, my, 0.45f) - WALL_H * 0.72f)
                lineTo(lerp(bx, mx, 0.45f), lerp(by, my, 0.45f))
                close()
            }
            canvas.drawPath(recess, PaintKit.solid(PaintKit.shade(color, 0.35f)))
        }
    }

    /** Markierungen für alle Türkacheln („Türmatte" in Akzentfarbe). */
    fun drawDoorMarkers(canvas: Canvas, room: Room) {
        room.doors.forEach { door ->
            val (tx, ty) = door.tile(room.w, room.h)
            val cx = IsoMath.tileCenterX(tx, ty)
            val cy = IsoMath.tileCenterY(tx, ty)
            canvas.drawPath(
                PaintKit.diamond(cx, cy, TW * 0.42f, TH * 0.42f),
                PaintKit.line(PaintKit.withAlpha(room.palette.accent, 200), TH * 0.07f),
            )
        }
    }

    /**
     * Beschriftung/Status aller Türen — wird NACH den Entities gezeichnet,
     * damit Requisiten und Figuren die Namen nicht verdecken.
     */
    fun drawDoorLabels(canvas: Canvas, room: Room, timeMs: Long, labelScale: Float = 1f) {
        room.doors.forEach { door ->
            when (door.wall) {
                Wall.N, Wall.W -> {
                    // Position = Mitte des Wandsegments (wie die Türnische)
                    val (ax, ay, bx, by) = if (door.wall == Wall.N) {
                        floatArrayOf(
                            IsoMath.worldX(door.pos.toFloat(), 0f), IsoMath.worldY(door.pos.toFloat(), 0f),
                            IsoMath.worldX(door.pos + 1f, 0f), IsoMath.worldY(door.pos + 1f, 0f),
                        )
                    } else {
                        floatArrayOf(
                            IsoMath.worldX(0f, door.pos.toFloat()), IsoMath.worldY(0f, door.pos.toFloat()),
                            IsoMath.worldX(0f, door.pos + 1f), IsoMath.worldY(0f, door.pos + 1f),
                        )
                    }
                    drawDoorDeco(canvas, door, (ax + bx) / 2f, (ay + by) / 2f - WALL_H * 0.5f, timeMs, labelScale)
                }
                Wall.S, Wall.O -> {
                    val (tx, ty) = door.tile(room.w, room.h)
                    drawDoorDeco(
                        canvas, door,
                        IsoMath.tileCenterX(tx, ty), IsoMath.tileCenterY(tx, ty) - TH * 0.9f,
                        timeMs, labelScale,
                    )
                }
            }
        }
    }

    /**
     * „?" für Unentdecktes, Schloss für Verriegeltes, Name darüber. Der Name
     * wird über [labelScale] (Basiszoom/Zoom) auf konstante Bildschirmgröße
     * gebracht — er wächst beim Pinch-Zoom nicht mit und bleibt dezent.
     */
    private fun drawDoorDeco(
        canvas: Canvas, door: Door, cx: Float, cy: Float, timeMs: Long, labelScale: Float,
    ) {
        if (door.targetRoomId == null) {
            val alpha = (140 + 100 * sin(timeMs / 400.0)).toInt().coerceIn(40, 240)
            val q = PaintKit.text
            q.textSize = TH * 0.55f
            q.color = PaintKit.withAlpha(PaintKit.WEISS, alpha)
            canvas.drawText("?", cx, cy + TH * 0.2f, q)
            q.color = PaintKit.WEISS
        }
        if (door.locked) {
            canvas.drawCircle(cx + TW * 0.18f, cy, TH * 0.09f, PaintKit.solid(PaintKit.GOLD))
        }
        val label = PaintKit.text
        label.textSize = TH * 0.16f * labelScale
        label.color = PaintKit.withAlpha(PaintKit.WEISS, 220)
        label.setShadowLayer(label.textSize * 0.18f, 0f, 0f, 0xCC000000.toInt())
        canvas.drawText(door.label, cx, cy - TH * 0.45f, label)
        label.clearShadowLayer()
        label.color = PaintKit.WEISS
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
