package com.castigaro.thellmadventure.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsoMathTest {

    @Test
    fun `Kachel zu Welt und zurueck ist verlustfrei`() {
        for (tx in -3..20) {
            for (ty in -3..20) {
                val fx = tx + 0.5f
                val fy = ty + 0.5f
                val wx = IsoMath.worldX(fx, fy)
                val wy = IsoMath.worldY(fx, fy)
                assertEquals(fx, IsoMath.tileX(wx, wy), 0.0001f)
                assertEquals(fy, IsoMath.tileY(wx, wy), 0.0001f)
            }
        }
    }

    @Test
    fun `pickTile trifft die Kachel unter ihrem Mittelpunkt`() {
        for (tx in 0 until 14) {
            for (ty in 0 until 10) {
                val wx = IsoMath.tileCenterX(tx, ty)
                val wy = IsoMath.tileCenterY(tx, ty)
                assertEquals(tx to ty, IsoMath.pickTile(wx, wy, 14, 10))
            }
        }
    }

    @Test
    fun `pickTile liefert null ausserhalb des Raums`() {
        // Punkt links neben Kachel (0,0): negative Kachelkoordinate
        val wx = IsoMath.tileCenterX(0, 0) - IsoMath.TILE_W
        val wy = IsoMath.tileCenterY(0, 0)
        assertNull(IsoMath.pickTile(wx, wy, 14, 10))
        // Punkt hinter der letzten Kachel
        assertNull(IsoMath.pickTile(IsoMath.tileCenterX(14, 10), IsoMath.tileCenterY(14, 10), 14, 10))
    }

    @Test
    fun `Zeichenreihenfolge sortiert hinten vor vorn`() {
        // (0,0) liegt hinter (1,0)
        assertTrue(IsoMath.drawOrder(0, 0, 1, 0) < 0)
        // Gleiche Summe: kleineres ty zuerst
        assertTrue(IsoMath.drawOrder(2, 0, 0, 2) < 0)
        // Identisch: stabil
        assertEquals(0, IsoMath.drawOrder(3, 4, 3, 4))
        // (5,5) liegt vor (1,1)
        assertTrue(IsoMath.drawOrder(5, 5, 1, 1) > 0)
    }

    @Test
    fun `Raumgrenzen umschliessen alle Kachelmittelpunkte`() {
        val w = 14
        val h = 10
        for (tx in 0 until w) {
            for (ty in 0 until h) {
                val wx = IsoMath.tileCenterX(tx, ty)
                val wy = IsoMath.tileCenterY(tx, ty)
                assertTrue(wx >= IsoMath.roomMinX(h))
                assertTrue(wx <= IsoMath.roomMaxX(w))
                assertTrue(wy >= IsoMath.roomMinY())
                assertTrue(wy <= IsoMath.roomMaxY(w, h))
            }
        }
    }
}
