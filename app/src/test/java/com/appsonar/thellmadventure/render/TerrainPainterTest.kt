package com.appsonar.thellmadventure.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerrainPainterTest {

    @Test
    fun `Kachel-Hash ist deterministisch`() {
        assertEquals(TerrainPainter.hash(3, 7), TerrainPainter.hash(3, 7))
        assertEquals(TerrainPainter.hash(-12, 5), TerrainPainter.hash(-12, 5))
    }

    @Test
    fun `Kachel-Hash ist nie negativ auch bei negativen Koordinaten`() {
        for (tx in -30..30 step 7) {
            for (ty in -30..30 step 5) {
                assertTrue("hash($tx,$ty) muss >= 0 sein", TerrainPainter.hash(tx, ty) >= 0)
            }
        }
    }

    @Test
    fun `benachbarte Kacheln streuen unterschiedlich`() {
        // Kein harter Verteilungstest — nur: nicht alle Nachbarn identisch.
        val werte = (0..8).map { TerrainPainter.hash(it % 3, it / 3) }.toSet()
        assertTrue("Nachbar-Hashes dürfen nicht kollabieren", werte.size > 5)
    }
}
