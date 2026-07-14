package com.castigaro.thellmadventure.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.castigaro.thellmadventure.world.DemoWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class SaveStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun aufraeumen() {
        SaveStore.resetForTest()
        File(context.filesDir, "saves").deleteRecursively()
    }

    @Test
    fun `Spielstand ueberlebt Speichern und Neuladen von der Platte`() {
        val save = DemoWorld.newSave("Welt A")
        SaveStore.add(context, save)

        SaveStore.resetForTest() // In-Memory-Cache verwerfen → Datei zählt
        val geladen = SaveStore.get(context, save.id)!!

        assertEquals("Welt A", geladen.name)
        assertEquals(save.world.rooms.keys, geladen.world.rooms.keys)
    }

    @Test
    fun `jeder Spielstand bekommt seine eigene Datei`() {
        val a = DemoWorld.newSave("Welt A")
        val b = DemoWorld.newSave("Welt B")
        SaveStore.add(context, a)
        SaveStore.add(context, b)

        assertTrue(SaveStore.fileFor(context, a.id).exists())
        assertTrue(SaveStore.fileFor(context, b.id).exists())
    }

    @Test
    fun `update schreibt nur die eigene Datei`() {
        val a = DemoWorld.newSave("Welt A")
        val b = DemoWorld.newSave("Welt B")
        SaveStore.add(context, a)
        SaveStore.add(context, b)

        val bInhaltVorher = SaveStore.fileFor(context, b.id).readText()
        a.name = "Welt A umbenannt"
        SaveStore.update(context, a)

        assertEquals(bInhaltVorher, SaveStore.fileFor(context, b.id).readText())
        SaveStore.resetForTest()
        assertEquals("Welt A umbenannt", SaveStore.get(context, a.id)!!.name)
    }

    @Test
    fun `atomarer Write laesst keine Temp-Datei zurueck`() {
        val save = DemoWorld.newSave("Welt A")
        SaveStore.add(context, save)
        SaveStore.update(context, save)

        val reste = File(context.filesDir, "saves")
            .listFiles { f -> f.name.endsWith(".tmp") }
        assertEquals(0, reste?.size ?: 0)
    }

    @Test
    fun `delete entfernt Datei und Cache-Eintrag`() {
        val save = DemoWorld.newSave("Welt A")
        SaveStore.add(context, save)
        SaveStore.delete(context, save)

        assertFalse(SaveStore.fileFor(context, save.id).exists())
        assertNull(SaveStore.get(context, save.id))
        SaveStore.resetForTest()
        assertNull(SaveStore.get(context, save.id))
    }

    @Test
    fun `newest liefert den zuletzt aktualisierten Stand`() {
        val alt = DemoWorld.newSave("Alt")
        val neu = DemoWorld.newSave("Neu")
        SaveStore.add(context, alt)
        SaveStore.add(context, neu)
        alt.updatedAt = 1000L
        neu.updatedAt = 2000L
        SaveStore.add(context, alt)
        SaveStore.add(context, neu)

        assertEquals("Neu", SaveStore.newest(context)!!.name)
    }

    @Test
    fun `kaputte Datei wird beim Laden uebersprungen`() {
        val save = DemoWorld.newSave("Welt A")
        SaveStore.add(context, save)
        File(context.filesDir, "saves/kaputt.json").writeText("{nicht json")

        SaveStore.resetForTest()
        assertEquals(1, SaveStore.getAll(context).size)
    }
}
