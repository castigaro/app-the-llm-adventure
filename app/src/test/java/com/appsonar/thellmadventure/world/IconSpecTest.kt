package com.appsonar.thellmadventure.world

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric wegen org.json — im Unit-Test-android.jar ist es nur gestubbt.
@RunWith(RobolectricTestRunner::class)
class IconSpecTest {

    @Test
    fun `JSON-Roundtrip erhaelt alle neuen Felder`() {
        val spec = IconSpec(
            colorIdx = 4,
            motiv = IconArchetype.LATERNE,
            material = IconMaterial.MESSING,
            leuchtet = true,
            abgenutzt = true,
        )
        val geladen = IconSpec.fromJson(spec.toJson())
        assertEquals(IconArchetype.LATERNE, geladen.motiv)
        assertEquals(IconMaterial.MESSING, geladen.material)
        assertEquals(4, geladen.colorIdx)
        assertTrue(geladen.leuchtet)
        assertTrue(geladen.abgenutzt)
    }

    @Test
    fun `Alt-JSON ohne Motiv parst und liefert ein sinnvolles Motiv`() {
        // Spielstand aus der Zeit vor den Detail-Icons: nur shape/colorIdx/glyph.
        val alt = JSONObject().put("shape", "SCHLUESSEL").put("colorIdx", 3).put("glyph", "K")
        val spec = IconSpec.fromJson(alt)
        assertNull(spec.motiv)
        assertFalse(spec.leuchtet)
        // Die Alt-Form entscheidet, wenn auch der Name nichts hergibt
        val (motiv, material) = spec.motivUndMaterial("Xyzzy")
        assertEquals(IconArchetype.SCHLUESSEL, motiv)
        assertEquals(IconMaterial.MESSING, material)
    }

    @Test
    fun `Namens-Schluesselwort schlaegt die Alt-Form`() {
        val alt = IconSpec(shape = IconShape.KREIS)
        val (motiv, material) = alt.motivUndMaterial("Rostiger Schlüssel")
        assertEquals(IconArchetype.SCHLUESSEL, motiv)
        assertEquals(IconMaterial.MESSING, material)
    }

    @Test
    fun `explizites Motiv schlaegt die Namens-Ableitung`() {
        val spec = IconSpec(motiv = IconArchetype.MUENZE, material = IconMaterial.STEIN)
        val (motiv, material) = spec.motivUndMaterial("Rostiger Schlüssel")
        assertEquals(IconArchetype.MUENZE, motiv)
        assertEquals(IconMaterial.STEIN, material)
    }

    @Test
    fun `Ableitung erkennt Spezifisches vor Allgemeinem`() {
        // "Schraubenschlüssel" ist Werkzeug, kein Schlüssel
        assertEquals(IconArchetype.WERKZEUG, IconSpec.derived("Schraubenschlüssel").motiv)
        // "Edelstein" ist Kristall, kein Stein
        assertEquals(IconArchetype.KRISTALL, IconSpec.derived("Edelstein").motiv)
        // "Zahnrad" ist kein Knochen
        assertEquals(IconArchetype.ZAHNRAD, IconSpec.derived("Messing-Zahnrad").motiv)
    }

    @Test
    fun `leuchtende Motive werden bei der Ableitung markiert`() {
        assertTrue(IconSpec.derived("Alte Fackel").leuchtet)
        assertTrue(IconSpec.derived("Sturmlaterne").leuchtet)
        assertFalse(IconSpec.derived("Holzeimer").leuchtet)
    }

    @Test
    fun `Hash-Fallback ist deterministisch und immer gueltig`() {
        val a = IconSpec.derived("Xyzzy-Artefakt")
        val b = IconSpec.derived("Xyzzy-Artefakt")
        assertEquals(a.motiv, b.motiv)
        assertEquals(a.material, b.material)
        assertEquals(a.colorIdx, b.colorIdx)
        assertTrue(a.colorIdx in SLOT_COLORS.indices)
    }
}
