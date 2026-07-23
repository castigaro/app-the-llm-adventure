package com.appsonar.thellmadventure.ui

import com.appsonar.thellmadventure.world.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CraftingControllerTest {

    private fun item(name: String) = Item("i-$name", name)

    @Test
    fun `Namen werden mit Plus verbunden`() {
        val text = CraftingController.slotNamesText(
            listOf(item("Handspiegel"), item("Feuerstein"), item("Zunder")),
        )
        assertEquals("Handspiegel + Feuerstein + Zunder", text)
    }

    @Test
    fun `offener Slot ergaenzt Auslassungspunkte`() {
        val text = CraftingController.slotNamesText(
            listOf(item("Handspiegel"), item("Feuerstein"), null),
        )
        assertEquals("Handspiegel + Feuerstein + …", text)
    }

    @Test
    fun `leere Bank ergibt null`() {
        assertNull(CraftingController.slotNamesText(listOf(null, null, null)))
    }
}
