package com.appsonar.thellmadventure.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.appsonar.thellmadventure.world.DemoWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class StoryTemplatesTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun aufraeumen() {
        StoryTemplates.resetForTest()
        File(context.filesDir, "templates.json").delete()
    }

    @Test
    fun `Schnappschuss behaelt die Welt aber entfernt Gespraechsverlaeufe`() {
        val save = DemoWorld.newSave("Laufende Partie")
        save.historyFor("n1").add(DialogMessage(DialogMessage.ROLE_NPC, "Hallo."))
        save.playtimeMs = 999_000L

        val template = StoryTemplates.saveFrom(context, save, "Meine Geschichte")
        val neu = StoryTemplates.newSaveFrom(template, "Neuer Lauf")

        // Welt (Räume, Fakten) bleibt erhalten
        assertEquals(save.world.rooms.keys, neu.world.rooms.keys)
        assertTrue(neu.world.facts.isNotEmpty())
        // Verläufe und Spielzeit sind bereinigt, neue Id
        assertTrue(neu.dialogHistories.isEmpty())
        assertEquals(0L, neu.playtimeMs)
        assertFalse(neu.finished)
        assertTrue(neu.id != save.id)
        // Spieler steht draußen auf einer begehbaren Kachel
        assertTrue(neu.world.outdoor!!.walkable(neu.ptx, neu.pty))
    }

    @Test
    fun `Vorlage ueberlebt Speichern und Neuladen von der Platte`() {
        val save = DemoWorld.newSave("Partie")
        StoryTemplates.saveFrom(context, save, "Gesichert")

        StoryTemplates.resetForTest()
        val geladen = StoryTemplates.getAll(context)
        assertEquals(1, geladen.size)
        assertEquals("Gesichert", geladen.first().name)
    }

    @Test
    fun `delete entfernt die Vorlage`() {
        val save = DemoWorld.newSave("Partie")
        val t = StoryTemplates.saveFrom(context, save, "Weg damit")
        StoryTemplates.delete(context, t)
        assertTrue(StoryTemplates.getAll(context).isEmpty())
    }
}
