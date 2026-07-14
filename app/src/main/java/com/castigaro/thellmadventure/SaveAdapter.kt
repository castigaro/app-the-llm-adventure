package com.castigaro.thellmadventure

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.castigaro.thellmadventure.data.GameSave
import java.text.DateFormat
import java.util.Date

/**
 * Liste der Spielstände auf dem Hauptbildschirm: Tippen lädt, langes
 * Drücken löscht (Muster aus Kap-Krakenzahn).
 */
class SaveAdapter(
    private val onTap: (GameSave) -> Unit,
    private val onLongPress: (GameSave) -> Unit,
) : RecyclerView.Adapter<SaveAdapter.Holder>() {

    private var saves: List<GameSave> = emptyList()

    fun submit(saves: List<GameSave>) {
        this.saves = saves
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_savegame, parent, false)
        return Holder(view)
    }

    override fun getItemCount(): Int = saves.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val save = saves[position]
        holder.name.text = save.name
        val stamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
            .format(Date(save.updatedAt))
        val raeume = save.world.rooms.size
        holder.details.text = holder.itemView.context.getString(
            R.string.save_details, stamp, raeume,
        )
        holder.itemView.setOnClickListener { onTap(save) }
        holder.itemView.setOnLongClickListener {
            onLongPress(save)
            true
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.saveName)
        val details: TextView = view.findViewById(R.id.saveDetails)
    }
}
