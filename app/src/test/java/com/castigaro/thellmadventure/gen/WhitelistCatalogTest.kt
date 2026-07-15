package com.castigaro.thellmadventure.gen

import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.game.Effect
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Npc
import com.castigaro.thellmadventure.world.Prop
import com.castigaro.thellmadventure.world.PropType
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.WorldState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistCatalogTest {

    private fun save(): GameSave {
        val world = WorldState()
        val room = Room("r1", "Halle", 8, 6)
        room.props.add(Prop("p1", PropType.TERMINAL, 2, 1, "Terminal"))
        room.doors.add(com.castigaro.thellmadventure.world.Door(
            com.castigaro.thellmadventure.world.Wall.O, 3, locked = true, label = "Luke",
        ))
        world.addRoom(room)
        return GameSave(name = "T", world = world, roomId = "r1")
    }

    @Test
    fun `nicht gewhitelistete Effekte werden verworfen`() {
        val save = save()
        val prop = save.world.room("r1")!!.props.first()
        val erlaubt = WhitelistCatalog.forMutation(save, "r1", prop, null).toSet()

        val gemeldet = listOf(
            "zustand:p1.an=an",       // erlaubt
            "zustand:p99.an=an",      // Requisite existiert hier nicht → nicht gewhitelistet
            "ende",                    // nicht gewhitelistet
            "loesche_alles",           // Unsinn
        )
        val effekte = WhitelistCatalog.resolve(save, gemeldet, erlaubt)

        assertEquals(1, effekte.size)
        assertTrue(effekte.first() is Effect.SetPropState)
    }

    @Test
    fun `fakt ist immer erlaubt auch ohne Whitelist-Eintrag`() {
        val save = save()
        val effekte = WhitelistCatalog.resolve(save, listOf("fakt:Der Boden knarrt"), emptySet())
        assertEquals(1, effekte.size)
        assertTrue(effekte.first() is Effect.AddFact)
    }

    @Test
    fun `oeffne loest die passende verschlossene Tuer`() {
        val save = save()
        val room = save.world.room("r1")!!
        val erlaubt = WhitelistCatalog.forMutation(save, room.id, room.props.first(), null).toSet()
        val effekte = WhitelistCatalog.resolve(save, listOf("oeffne:r1.O.3"), erlaubt)
        assertEquals(1, effekte.size)
        assertTrue(effekte.first() is Effect.UnlockDoor)
    }

    @Test
    fun `Dialog-Whitelist erlaubt Uebergabe der von der Figur gehaltenen Items`() {
        val save = save()
        val npc = Npc("n1", "Jorin", "r1", 4, 3, rolle = "Mechaniker")
        save.world.addNpc(npc)
        save.world.room("r1")!!.npcIds.add("n1")
        val item = Item("i1", "Ring", holder = Item.holderNpc("n1"))
        save.world.addItem(item)

        val erlaubt = WhitelistCatalog.forDialog(save, npc)
        assertTrue(erlaubt.contains("gib:i1"))

        val effekte = WhitelistCatalog.resolve(save, listOf("gib:i1"), erlaubt.toSet())
        assertEquals(1, effekte.size)
        assertTrue(effekte.first() is Effect.GiveItemToPlayer)
    }

    @Test
    fun `erhalte akzeptiert nur den separat validierten neuen Gegenstand`() {
        val save = save()
        val npc = Npc("n1", "Krämer", "r1", 4, 3, rolle = "Händler")
        save.world.addNpc(npc)
        val echt = Item("i5", "Frisch geschmiedeter Haken", holder = Item.holderNpc("n1"))
        save.world.addItem(echt)

        // erhalte:i5 nur gültig, wenn i5 als neuesItemId übergeben wird
        val ohne = WhitelistCatalog.resolve(save, listOf("erhalte:i5"), emptySet(), neuesItemId = null)
        assertTrue(ohne.isEmpty())

        val mit = WhitelistCatalog.resolve(save, listOf("erhalte:i5"), emptySet(), neuesItemId = "i5")
        assertEquals(1, mit.size)
        assertTrue(mit.first() is Effect.GiveItemToPlayer)
    }
}
