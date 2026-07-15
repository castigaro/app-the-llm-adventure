package com.castigaro.thellmadventure.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.castigaro.thellmadventure.R
import com.castigaro.thellmadventure.world.IconSpec
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Skill

/**
 * Adapter der minimierten Inventarleiste: nur Icons, Tap wählt einen
 * Gegenstand als aktiven „Benutze mit …"-Gegenstand.
 */
class InventoryBarAdapter(
    private val onTap: (Item) -> Unit,
) : RecyclerView.Adapter<InventoryBarAdapter.Holder>() {

    private var items: List<Item> = emptyList()
    private var selectedId: String? = null

    fun submit(items: List<Item>, selectedId: String?) {
        this.items = items
        this.selectedId = selectedId
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inventory_slot, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.icon.bind(item.icon, item.name)
        holder.icon.highlight = item.id == selectedId
        holder.icon.contentDescription = item.name
        holder.itemView.setOnClickListener { onTap(item) }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: IconView = view.findViewById(R.id.icon)
    }
}

/** Zeilentypen des ausgeklappten Panels. */
sealed class InventoryRow {
    class Header(val text: String) : InventoryRow()
    class ItemRow(val item: Item) : InventoryRow()
    class SkillRow(val skill: Skill) : InventoryRow()
}

/**
 * Adapter des ausgeklappten, scrollbaren Panels: Icon + Name + Beschreibung,
 * mit Abschnitts-Überschriften für Gegenstände und Fähigkeiten.
 */
class InventoryListAdapter(
    private val onItemTap: (Item) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<InventoryRow> = emptyList()
    private var selectedId: String? = null

    fun submit(rows: List<InventoryRow>, selectedId: String?) {
        this.rows = rows
        this.selectedId = selectedId
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is InventoryRow.Header -> TYPE_HEADER
        else -> TYPE_ROW
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_HEADER) {
            HeaderHolder(inflater.inflate(R.layout.row_inventory_header, parent, false))
        } else {
            RowHolder(inflater.inflate(R.layout.row_inventory_expanded, parent, false))
        }
    }

    override fun getItemCount(): Int = rows.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is InventoryRow.Header -> (holder as HeaderHolder).header.text = row.text
            is InventoryRow.ItemRow -> (holder as RowHolder).bind(
                row.item.icon,
                row.item.name,
                row.item.beschreibung,
                row.item.id == selectedId,
            ) { onItemTap(row.item) }
            is InventoryRow.SkillRow -> (holder as RowHolder).bind(
                row.skill.icon,
                row.skill.name,
                row.skill.beschreibung,
                selected = false,
                onTap = null,
            )
        }
    }

    class HeaderHolder(view: View) : RecyclerView.ViewHolder(view) {
        val header: TextView = view.findViewById(R.id.header)
    }

    class RowHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val icon: IconView = view.findViewById(R.id.icon)
        private val name: TextView = view.findViewById(R.id.name)
        private val description: TextView = view.findViewById(R.id.description)

        fun bind(
            spec: IconSpec,
            title: String,
            subtitle: String,
            selected: Boolean,
            onTap: (() -> Unit)?,
        ) {
            icon.bind(spec, title)
            icon.highlight = selected
            name.text = title
            description.text = subtitle
            description.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
            itemView.setOnClickListener(if (onTap != null) View.OnClickListener { onTap() } else null)
            itemView.isClickable = onTap != null
        }
    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_ROW = 1
    }
}
