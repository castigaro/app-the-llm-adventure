package com.appsonar.thellmadventure.render

import com.appsonar.thellmadventure.world.Prop
import com.appsonar.thellmadventure.world.PropType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameGateTest {

    @Test
    fun `stehendes Bild ohne Animiertes stoppt die Schleife`() {
        assertEquals(
            FrameGate.Modus.IDLE,
            FrameGate.modus(bewegung = false, kameraRuht = true, ambientSichtbar = false),
        )
    }

    @Test
    fun `Bewegung oder wandernde Kamera erzwingen volle Bildrate`() {
        assertEquals(
            FrameGate.Modus.VSYNC,
            FrameGate.modus(bewegung = true, kameraRuht = true, ambientSichtbar = false),
        )
        assertEquals(
            FrameGate.Modus.VSYNC,
            FrameGate.modus(bewegung = false, kameraRuht = false, ambientSichtbar = true),
        )
    }

    @Test
    fun `sichtbares Geschimmer erzwingt den Ambient-Takt`() {
        assertEquals(
            FrameGate.Modus.AMBIENT,
            FrameGate.modus(bewegung = false, kameraRuht = true, ambientSichtbar = true),
        )
    }

    @Test
    fun `animierte Requisiten werden am Typ und Zustand erkannt`() {
        // Ofen animiert nur, wenn er an ist
        val ofen = Prop("p1", PropType.OFEN, 1, 1, "Ofen")
        assertFalse(FrameGate.propAnimiert(ofen))
        ofen.states["an"] = true
        assertTrue(FrameGate.propAnimiert(ofen))

        // Lampen leuchten standardmäßig, außer explizit aus
        val lampe = Prop("p2", PropType.LAMPE, 1, 2, "Lampe")
        assertTrue(FrameGate.propAnimiert(lampe))
        lampe.states["an"] = false
        assertFalse(FrameGate.propAnimiert(lampe))

        // Kristalle pulsieren immer, Kisten nie
        assertTrue(FrameGate.propAnimiert(Prop("p3", PropType.KRISTALL, 1, 3, "Kristall")))
        assertFalse(FrameGate.propAnimiert(Prop("p4", PropType.KISTE, 1, 4, "Kiste")))
    }

    @Test
    fun `Gegenstands-Funkeln macht jede Requisite animiert`() {
        val kiste = Prop("p1", PropType.KISTE, 1, 1, "Kiste", itemId = "i1")
        assertTrue(FrameGate.propAnimiert(kiste))
    }
}
