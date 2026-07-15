package com.castigaro.thellmadventure.game

import com.castigaro.thellmadventure.world.DemoWorld
import com.castigaro.thellmadventure.world.GoalType
import com.castigaro.thellmadventure.world.SecretGoal
import com.castigaro.thellmadventure.world.Story
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StateDigestTest {

    private fun testSave() = DemoWorld.newSave("Test").also { save ->
        save.world.story = Story(
            titel = "Das Echo der Halle",
            praemisse = "Jemand hat das Licht angelassen.",
            ton = "still",
            geheimesZiel = SecretGoal(GoalType.BESITZE_ITEM, "i1", umschreibung = "Der Schlüssel"),
        )
    }

    @Test
    fun `Digest enthaelt Rahmen Story Gebiet Raeume Inventar und Spieler`() {
        val digest = StateDigest.build(testSave())

        assertTrue(digest.contains("RAHMEN: Requisiten/Farben/Icons nur aus dem Katalog"))
        assertTrue(digest.contains("STORY „Das Echo der Halle“"))
        assertTrue(digest.contains("GEHEIMES ZIEL: besitze_item i1"))
        assertTrue(digest.contains("GEBIET"))
        assertTrue(digest.contains("g1 Sturmwiese (0,0) moos"))
        // Der Innenraum hinter dem Gebäude der aktuellen Parzelle ist detailliert
        assertTrue(digest.contains("RÄUME:"))
        assertTrue(digest.contains("r1 Stube der Kate 8x6 rost"))
        assertTrue(digest.contains("INVENTAR: leer"))
        assertTrue(digest.contains("SPIELER: draußen in g1 Sturmwiese, Kachel(5,6)"))
    }

    @Test
    fun `Parzellenzeile zeigt Gebaeude mit Ziel und Unentdecktem`() {
        val digest = StateDigest.build(testSave())

        // Kate mit fertigem Innenraum, Turm noch unentdeckt ("?")
        assertTrue(digest.contains("b1 Alte Kate→r1"))
        assertTrue(digest.contains("b2 Verfallener Turm→?"))
        // Die Rücktür der Stube führt nach draußen, die Kellerluke ins Ungewisse
        assertTrue(digest.contains("S→welt:b1"))
        assertTrue(digest.contains("O→?"))
    }

    @Test
    fun `aufgenommenes Item verschwindet aus dem Raum und erscheint im Inventar`() {
        val save = testSave()
        val kiste = save.world.rooms.values.flatMap { it.props }
            .first { it.itemId != null && it.states["verschlossen"] != true }
        val itemId = kiste.itemId!!
        val itemName = save.world.items[itemId]!!.name

        val vorher = StateDigest.build(save)
        assertTrue(vorher.contains("enthält $itemId"))
        assertTrue(vorher.contains("INVENTAR: leer"))

        GameEngine.apply(save, listOf(Effect.TakeItem(itemId)))

        val nachher = StateDigest.build(save)
        assertFalse(nachher.contains("enthält $itemId"))
        assertTrue(nachher.contains("INVENTAR: $itemId $itemName"))
        assertTrue(nachher.contains("aufgenommen"))
    }

    @Test
    fun `Fakten werden auf das Maximum gekappt neueste zuerst`() {
        val save = testSave()
        for (i in 1..30) save.world.addFact("Ereignis $i")

        val digest = StateDigest.build(save)
        val faktenZeile = digest.lines().first { it.startsWith("FAKTEN") }

        assertTrue(faktenZeile.contains("Ereignis 30"))
        assertFalse(faktenZeile.contains("Ereignis 5;"))
        // Neueste zuerst: Ereignis 30 steht vor Ereignis 29
        assertTrue(faktenZeile.indexOf("Ereignis 30") < faktenZeile.indexOf("Ereignis 29"))
    }

    @Test
    fun `entfernte Raeume erscheinen nur namentlich`() {
        val save = testSave()
        // Ein Raum ohne Verbindung zur aktuellen Parzelle bleibt namentlich.
        val fern = com.castigaro.thellmadventure.world.Room(
            id = save.world.nextId("r"), name = "Fernes Lager", w = 6, h = 4,
        )
        save.world.addRoom(fern)

        val digest = StateDigest.build(save)
        assertTrue(digest.contains("(weitere: ${fern.id} Fernes Lager)"))
        assertFalse(digest.contains("${fern.id} Fernes Lager 6x4"))
    }

    @Test
    fun `Rezepte erscheinen inklusive unmoeglicher Kombinationen`() {
        val save = testSave()
        save.world.recipes.add(
            com.castigaro.thellmadventure.world.Recipe("c1", listOf("i1", "i2"), "i9")
        )
        save.world.recipes.add(
            com.castigaro.thellmadventure.world.Recipe("c2", listOf("i1", "i3"), null)
        )

        val digest = StateDigest.build(save)
        assertTrue(digest.contains("c1 i1+i2→i9"))
        assertTrue(digest.contains("c2 i1+i3→unmöglich"))
    }
}
