package com.castigaro.thellmadventure.data

import com.castigaro.thellmadventure.world.DemoWorld
import com.castigaro.thellmadventure.world.GoalType
import com.castigaro.thellmadventure.world.Palette
import com.castigaro.thellmadventure.world.SecretGoal
import com.castigaro.thellmadventure.world.Story
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric liefert die echte org.json-Implementierung für JVM-Tests.
@RunWith(RobolectricTestRunner::class)
class GameSaveTest {

    @Test
    fun `Spielstand uebersteht JSON-Roundtrip verlustfrei`() {
        val original = DemoWorld.newSave("Testwelt")
        original.world.story = Story(
            titel = "Das Echo der Halle",
            praemisse = "Jemand hat das Licht angelassen.",
            ton = "melancholisch",
            geheimesZiel = SecretGoal(GoalType.BESITZE_ITEM, "i1", umschreibung = "Der Schlüssel."),
            basisStory = "mondstation",
        )
        original.historyFor("n1").add(DialogMessage(DialogMessage.ROLE_NPC, "Hallo."))

        val geladen = GameSave.fromJson(original.toJson())

        assertEquals(original.id, geladen.id)
        assertEquals("Testwelt", geladen.name)
        assertEquals(original.roomId, geladen.roomId)
        assertEquals(original.ptx to original.pty, geladen.ptx to geladen.pty)
        assertEquals(original.world.rooms.keys, geladen.world.rooms.keys)
        assertEquals(original.world.items.keys, geladen.world.items.keys)
        assertEquals(original.world.facts, geladen.world.facts)
        assertEquals("Das Echo der Halle", geladen.world.story?.titel)
        assertEquals(GoalType.BESITZE_ITEM, geladen.world.story?.geheimesZiel?.typ)
        assertEquals(1, geladen.historyFor("n1").size)

        val raum = geladen.world.room(original.roomId)!!
        val originalRaum = original.world.room(original.roomId)!!
        assertEquals(originalRaum.props.size, raum.props.size)
        assertEquals(originalRaum.doors.size, raum.doors.size)
        assertEquals(Palette.STAHL, raum.palette)
        // Zustände und Item-Verweise bleiben erhalten
        val truhe = raum.props.first { it.states["verschlossen"] == true }
        assertNotNull(truhe.itemId)
    }

    @Test
    fun `unbekannte Enum-Namen werden tolerant verworfen`() {
        val save = DemoWorld.newSave("Testwelt")
        val json = save.toJson()
        val raum = json.getJSONObject("world").getJSONArray("rooms").getJSONObject(0)
        // Palette kaputt machen → Default; einen Prop-Typ kaputt machen → Prop fliegt raus
        raum.put("palette", "NEON_PINK")
        val props = raum.getJSONArray("props")
        val propCount = props.length()
        props.getJSONObject(0).put("type", "WARPKERN")

        val geladen = GameSave.fromJson(json)
        val geladenRaum = geladen.world.room(save.roomId)!!

        assertEquals(Palette.STAHL, geladenRaum.palette)
        assertEquals(propCount - 1, geladenRaum.props.size)
    }

    @Test
    fun `ID-Zaehler bleiben nach Laden eindeutig`() {
        val save = DemoWorld.newSave("Testwelt")
        val vorher = save.world.items.keys.toSet()
        val geladen = GameSave.fromJson(save.toJson())
        val neueId = geladen.world.nextId("i")
        assertTrue("$neueId darf nicht kollidieren", neueId !in vorher)
    }

    @Test
    fun `cloneAs erzeugt tiefe Kopie mit neuer Id`() {
        val save = DemoWorld.newSave("Testwelt")
        val clone = save.cloneAs("Sicherung")
        assertTrue(clone.id != save.id)
        assertEquals("Sicherung", clone.name)
        // Tiefe: Änderung am Klon berührt das Original nicht
        clone.world.room(save.roomId)!!.props.removeAt(0)
        assertTrue(
            clone.world.room(save.roomId)!!.props.size <
                save.world.room(save.roomId)!!.props.size
        )
    }

    @Test
    fun `Story ohne geheimes Ziel wird verworfen`() {
        val json = JSONObject().apply {
            put("titel", "Kaputt")
            put("praemisse", "…")
        }
        assertNull(Story.fromJson(json))
    }
}
