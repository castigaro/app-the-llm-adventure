package com.appsonar.thellmadventure.game

import com.appsonar.thellmadventure.data.GameSave
import com.appsonar.thellmadventure.world.GoalType
import com.appsonar.thellmadventure.world.Item
import com.appsonar.thellmadventure.world.Prop
import com.appsonar.thellmadventure.world.PropType
import com.appsonar.thellmadventure.world.Room
import com.appsonar.thellmadventure.world.SecretGoal
import com.appsonar.thellmadventure.world.Story
import com.appsonar.thellmadventure.world.WorldState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoalCheckerTest {

    private fun saveWithGoal(goal: SecretGoal): GameSave {
        val world = WorldState()
        world.addRoom(Room("r1", "Start", 6, 4))
        world.story = Story("T", "P", geheimesZiel = goal)
        return GameSave(name = "Test", world = world, roomId = "r1")
    }

    @Test
    fun `besitze_item ist erst erfuellt wenn das Item im Inventar liegt`() {
        val save = saveWithGoal(SecretGoal(GoalType.BESITZE_ITEM, "i1"))
        val item = Item("i1", "Kernschlüssel", holder = "versteckt")
        save.world.addItem(item)

        assertFalse(GoalChecker.isReached(save))
        item.holder = Item.HOLDER_INVENTORY
        assertTrue(GoalChecker.isReached(save))
    }

    @Test
    fun `erreiche_raum ist erfuellt wenn der Spieler dort steht`() {
        val save = saveWithGoal(SecretGoal(GoalType.ERREICHE_RAUM, "r9"))
        assertFalse(GoalChecker.isReached(save))
        save.roomId = "r9"
        assertTrue(GoalChecker.isReached(save))
    }

    @Test
    fun `zustand_gesetzt ist erfuellt wenn die Requisite den Zustand hat`() {
        val save = saveWithGoal(SecretGoal(GoalType.ZUSTAND_GESETZT, "p1", zustandKey = "an"))
        save.world.room("r1")!!.props.add(Prop("p1", PropType.TERMINAL, 1, 1, "Terminal"))
        assertFalse(GoalChecker.isReached(save))
        save.world.room("r1")!!.props.first().states["an"] = true
        assertTrue(GoalChecker.isReached(save))
    }

    @Test
    fun `ein beendetes Spiel meldet das Ziel nicht erneut`() {
        val save = saveWithGoal(SecretGoal(GoalType.ERREICHE_RAUM, "r1"))
        assertTrue(GoalChecker.isReached(save))
        save.finished = true
        assertFalse(GoalChecker.isReached(save))
    }

    @Test
    fun `erreiche_raum versteht draussen auch Parzellen-Ids`() {
        val save = saveWithGoal(SecretGoal(GoalType.ERREICHE_RAUM, "g1"))
        val outdoor = com.appsonar.thellmadventure.world.OutdoorMap()
        outdoor.addParcel(com.appsonar.thellmadventure.world.Parcel("g1", 0, 0, "Wiese"))
        save.world.outdoor = outdoor

        // Noch im Raum r1 → nicht erfüllt
        assertFalse(GoalChecker.isReached(save))

        save.roomId = com.appsonar.thellmadventure.data.GameSave.ORT_DRAUSSEN
        save.ptx = 5
        save.pty = 5
        assertTrue(GoalChecker.isReached(save))
    }

    @Test
    fun `zustand_gesetzt findet auch Requisiten draussen`() {
        val save = saveWithGoal(SecretGoal(GoalType.ZUSTAND_GESETZT, "p9", zustandKey = "an"))
        val outdoor = com.appsonar.thellmadventure.world.OutdoorMap()
        val parcel = com.appsonar.thellmadventure.world.Parcel("g1", 0, 0, "Wiese")
        parcel.props.add(Prop("p9", PropType.HEBEL, 4, 4, "Hebel"))
        outdoor.addParcel(parcel)
        save.world.outdoor = outdoor

        assertFalse(GoalChecker.isReached(save))
        parcel.props.first().states["an"] = true
        assertTrue(GoalChecker.isReached(save))
    }
}
