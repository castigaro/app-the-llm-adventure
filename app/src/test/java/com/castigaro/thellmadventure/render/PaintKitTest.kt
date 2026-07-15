package com.castigaro.thellmadventure.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric wegen android.graphics.Paint.
@RunWith(RobolectricTestRunner::class)
class PaintKitTest {

    @Test
    fun `solid liefert je Farbe dieselbe gecachte Instanz`() {
        val a = PaintKit.solid(0xFF123456.toInt())
        val b = PaintKit.solid(0xFF123456.toInt())
        assertSame(a, b)
        assertEquals(0xFF123456.toInt(), a.color)
        assertNotSame(a, PaintKit.solid(0xFF654321.toInt()))
    }

    @Test
    fun `line cached je Farbe UND Breite`() {
        val a = PaintKit.line(0xFF123456.toInt(), 4f)
        assertSame(a, PaintKit.line(0xFF123456.toInt(), 4f))
        assertEquals(4f, a.strokeWidth)
        // Gleiche Farbe, andere Breite → eigene Instanz
        val b = PaintKit.line(0xFF123456.toInt(), 8f)
        assertNotSame(a, b)
        assertEquals(8f, b.strokeWidth)
        // Gleiche Breite, andere Farbe → eigene Instanz
        assertNotSame(a, PaintKit.line(0xFF654321.toInt(), 4f))
    }
}
