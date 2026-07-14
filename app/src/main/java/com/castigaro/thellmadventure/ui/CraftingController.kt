package com.castigaro.thellmadventure.ui

import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.thellmadventure.databinding.ViewCraftingOverlayBinding
import com.castigaro.thellmadventure.world.Item

/**
 * Steuert das Minecraft-artige Crafting-Raster: drei Eingabe-Slots, ein
 * Ergebnis-Slot und darunter das Inventar zum Bestücken. Tippe einen Slot an,
 * dann einen Gegenstand aus dem Inventar; ein gefüllter Slot wird durch
 * erneutes Antippen geleert. „Verbinden" ist ab zwei belegten Slots aktiv.
 */
class CraftingController(
    private val binding: ViewCraftingOverlayBinding,
    private val inventory: () -> List<Item>,
    private val onCombine: (List<String>) -> Unit,
) {

    private val slots = arrayOfNulls<Item>(3)
    private var selectedSlot = 0
    private val slotViews = listOf(binding.craftSlot0, binding.craftSlot1, binding.craftSlot2)

    private val invAdapter = InventoryBarAdapter(::placeInSlot)

    init {
        binding.craftInventory.layoutManager =
            LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
        binding.craftInventory.adapter = invAdapter

        slotViews.forEachIndexed { index, view ->
            view.setOnClickListener { onSlotTap(index) }
        }
        binding.craftCombine.setOnClickListener {
            onCombine(slots.filterNotNull().map { it.id })
        }
        binding.craftCancel.setOnClickListener { close() }
    }

    fun open() {
        reset()
        refreshInventory()
        binding.root.visibility = View.VISIBLE
    }

    fun close() {
        binding.root.visibility = View.GONE
    }

    fun refreshInventory() {
        // Bereits in Slots liegende Gegenstände nicht doppelt anbieten.
        val used = slots.filterNotNull().map { it.id }.toSet()
        invAdapter.submit(inventory().filter { it.id !in used }, selectedId = null)
    }

    fun reset() {
        slots.fill(null)
        selectedSlot = 0
        renderSlots()
        binding.craftResult.spec = null
        binding.craftCombine.isEnabled = false
    }

    private fun onSlotTap(index: Int) {
        if (slots[index] != null) {
            // Gefüllten Slot leeren
            slots[index] = null
            selectedSlot = index
        } else {
            selectedSlot = index
        }
        renderSlots()
        refreshInventory()
        updateCombineState()
    }

    private fun placeInSlot(item: Item) {
        // In den ausgewählten (oder ersten freien) Slot legen.
        val target = if (slots[selectedSlot] == null) selectedSlot
        else slots.indexOfFirst { it == null }
        if (target < 0) return
        slots[target] = item
        selectedSlot = (target + 1).coerceAtMost(2)
        renderSlots()
        refreshInventory()
        updateCombineState()
    }

    private fun renderSlots() {
        slotViews.forEachIndexed { index, view ->
            view.spec = slots[index]?.icon
            view.highlight = index == selectedSlot
        }
    }

    private fun updateCombineState() {
        binding.craftCombine.isEnabled = slots.count { it != null } >= 2
    }
}
