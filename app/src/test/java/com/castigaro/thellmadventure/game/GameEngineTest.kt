package com.castigaro.thellmadventure.game

import com.castigaro.thellmadventure.world.DemoWorld
import com.castigaro.thellmadventure.world.IconSpec
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Skill
import com.castigaro.thellmadventure.world.Wall
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {

    @Test
    fun `TakeItem verschiebt den Gegenstand aus dem Raum ins Inventar`() {
        val save = DemoWorld.newSave("Test")
        val kiste = save.world.rooms.values.flatMap { it.props }
            .first { it.itemId != null && it.states["verschlossen"] != true }
        val itemId = kiste.itemId!!

        GameEngine.apply(save, listOf(Effect.TakeItem(itemId)))

        assertNull("Requisite darf den Gegenstand nicht mehr tragen", kiste.itemId)
        assertEquals(Item.HOLDER_INVENTORY, save.world.items[itemId]!!.holder)
        assertTrue(save.world.inventory().any { it.id == itemId })
        assertTrue(save.world.facts.last().contains("aufgenommen"))
    }

    @Test
    fun `ConsumeItem verbraucht nur Inventar-Gegenstaende`() {
        val save = DemoWorld.newSave("Test")
        val itemId = save.world.items.keys.first()
        // Liegt noch in einer Requisite → darf nicht verbraucht werden
        GameEngine.apply(save, listOf(Effect.ConsumeItem(itemId)))
        assertTrue(save.world.items[itemId]!!.holder.startsWith("prop:"))

        GameEngine.apply(save, listOf(Effect.TakeItem(itemId), Effect.ConsumeItem(itemId)))
        assertEquals(Item.HOLDER_CONSUMED, save.world.items[itemId]!!.holder)
        assertFalse(save.world.inventory().any { it.id == itemId })
    }

    @Test
    fun `GiveItemToPlayer holt den Gegenstand von der Figur`() {
        val save = DemoWorld.newSave("Test")
        val geschenk = Item(save.world.nextId("i"), "Amulett", holder = Item.holderNpc("n1"))
        save.world.addItem(geschenk)

        GameEngine.apply(save, listOf(Effect.GiveItemToPlayer(geschenk.id)))

        assertEquals(Item.HOLDER_INVENTORY, geschenk.holder)
        assertTrue(save.world.facts.last().contains("n1 gab ${geschenk.id}"))
    }

    @Test
    fun `SetPropState akzeptiert nur Katalog-Schluessel`() {
        val save = DemoWorld.newSave("Test")
        val prop = save.world.rooms.values.first().props.first()

        GameEngine.apply(save, listOf(Effect.SetPropState(prop.id, "explodiert", true)))
        assertFalse(prop.states.containsKey("explodiert"))

        GameEngine.apply(save, listOf(Effect.SetPropState(prop.id, "kaputt", true)))
        assertEquals(true, prop.states["kaputt"])
    }

    @Test
    fun `UnlockDoor entriegelt genau die eine Tuer`() {
        val save = DemoWorld.newSave("Test")
        val raum = save.world.rooms.values.first()
        raum.doors.add(
            com.castigaro.thellmadventure.world.Door(
                Wall.W, 2, locked = true, lockHint = "Klemmt.", label = "Luke",
            )
        )
        val zu = raum.doors.first { it.locked }

        GameEngine.apply(save, listOf(Effect.UnlockDoor(raum.id, zu.wall, zu.pos)))
        assertFalse(zu.locked)

        // Zweites Entriegeln ist wirkungslos (kein doppelter Fakt)
        val fakten = save.world.facts.size
        GameEngine.apply(save, listOf(Effect.UnlockDoor(raum.id, zu.wall, zu.pos)))
        assertEquals(fakten, save.world.facts.size)
    }

    @Test
    fun `UnlockDoor ignoriert unbekannte Tueren`() {
        val save = DemoWorld.newSave("Test")
        val fakten = save.world.facts.size
        GameEngine.apply(save, listOf(Effect.UnlockDoor("r99", Wall.N, 0)))
        assertEquals(fakten, save.world.facts.size)
    }

    @Test
    fun `TeachSkill und AddFact hinterlassen Fakten`() {
        val save = DemoWorld.newSave("Test")
        val skill = Skill(save.world.nextId("s"), "Schlossknacken", icon = IconSpec.derived("Schlossknacken"))
        save.world.skills[skill.id] = skill

        GameEngine.apply(save, listOf(Effect.TeachSkill(skill.id), Effect.AddFact("Der Hebel quietscht")))

        assertTrue(save.world.facts.any { it.contains("Schlossknacken") })
        assertTrue(save.world.facts.last().contains("Der Hebel quietscht"))
    }

    @Test
    fun `EndGame setzt finished`() {
        val save = DemoWorld.newSave("Test")
        GameEngine.apply(save, listOf(Effect.EndGame))
        assertTrue(save.finished)
    }
}
