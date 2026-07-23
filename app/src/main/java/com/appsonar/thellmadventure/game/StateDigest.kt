package com.appsonar.thellmadventure.game

import com.appsonar.thellmadventure.data.GameSave
import com.appsonar.thellmadventure.world.Limits
import com.appsonar.thellmadventure.world.OutdoorMap
import com.appsonar.thellmadventure.world.Parcel
import com.appsonar.thellmadventure.world.Room

/**
 * Verdichtet den kompletten Weltzustand in einen knappen deutschen
 * Lagebericht für die KI-Prompts — eine Zeile pro Entität, Kurz-IDs,
 * entfernte Räume nur namentlich, Fakten gekappt. Das ist das
 * tokensparende „Gedächtnis", über das die KI weiß, was es schon gibt
 * und was sie wiederverwenden kann.
 */
object StateDigest {

    fun build(save: GameSave): String {
        val world = save.world
        val sb = StringBuilder()

        sb.appendLine(
            "RAHMEN: Requisiten/Farben/Icons nur aus dem Katalog. " +
                "Effekte nur aus der Whitelist."
        )
        world.story?.let { story ->
            sb.appendLine(
                "STORY „${story.titel}“: ${story.praemisse} " +
                    "GEHEIMES ZIEL: ${story.geheimesZiel.typ.name.lowercase()} ${story.geheimesZiel.zielId}" +
                    (story.geheimesZiel.zustandKey?.let { ".$it" } ?: "") +
                    " (${story.geheimesZiel.umschreibung})"
            )
        }

        // Außenkarte: aktuelle Parzelle + Nachbarn in voller Zeile, Rest namentlich
        val outdoor = world.outdoor
        val currentParcel = if (save.roomId == GameSave.ORT_DRAUSSEN) {
            outdoor?.parcelForTile(save.ptx, save.pty)
        } else null
        if (outdoor != null && outdoor.parcels.isNotEmpty()) {
            val nahIds = buildSet {
                currentParcel?.let { p ->
                    add(p.id)
                    listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).forEach { (dx, dy) ->
                        outdoor.parcelAt(p.gx + dx, p.gy + dy)?.let { add(it.id) }
                    }
                }
            }
            sb.appendLine("GEBIET (Außenkarte, 12×12-Parzellen):")
            outdoor.parcels.values.filter { it.id in nahIds || nahIds.isEmpty() }
                .forEach { sb.appendLine(parcelLine(it, outdoor)) }
            val ferneParzellen = outdoor.parcels.values.filter { it.id !in nahIds && nahIds.isNotEmpty() }
            if (ferneParzellen.isNotEmpty()) {
                sb.appendLine("(weitere: ${ferneParzellen.joinToString(", ") { "${it.id} ${it.name}" }})")
            }
        }

        // Räume: aktueller + angrenzende in voller Zeile, Rest nur namentlich.
        // Draußen zählen die Innenräume hinter den Gebäuden der aktuellen
        // Parzelle als angrenzend.
        val currentRoom = world.room(save.roomId)
        val detailIds = buildSet {
            currentRoom?.let { room ->
                add(room.id)
                room.doors.mapNotNull { it.targetRoomId }.forEach { add(it) }
            }
            currentParcel?.buildingIds
                ?.mapNotNull { outdoor?.buildings?.get(it)?.targetRoomId }
                ?.forEach { add(it) }
        }
        if (world.rooms.isNotEmpty()) {
            sb.appendLine("RÄUME:")
            world.rooms.values.filter { it.id in detailIds }.forEach { sb.appendLine(roomLine(it)) }
            val ferne = world.rooms.values.filter { it.id !in detailIds }
            if (ferne.isNotEmpty()) {
                sb.appendLine("(weitere: ${ferne.joinToString(", ") { "${it.id} ${it.name}" }})")
            }
        }

        if (world.npcs.isNotEmpty()) {
            sb.appendLine("FIGUREN: " + world.npcs.values.joinToString("; ") { npc ->
                "${npc.id} ${npc.name}@${npc.roomId} (${npc.rolle})"
            })
        }

        val inventory = world.inventory()
        sb.appendLine(
            if (inventory.isEmpty()) "INVENTAR: leer"
            else "INVENTAR: " + inventory.joinToString("; ") { "${it.id} ${it.name}" }
        )

        if (world.skills.isNotEmpty()) {
            sb.appendLine("SKILLS: " + world.skills.values.joinToString("; ") { "${it.id} ${it.name}" })
        }

        if (world.recipes.isNotEmpty()) {
            sb.appendLine("REZEPTE: " + world.recipes.joinToString("; ") { recipe ->
                "${recipe.id} ${recipe.inputs.joinToString("+")}→${recipe.outputItemId ?: "unmöglich"}"
            })
        }

        val fakten = world.facts.takeLast(Limits.MAX_FAKTEN_DIGEST).reversed()
        if (fakten.isNotEmpty()) {
            sb.appendLine("FAKTEN(neueste zuerst, max ${Limits.MAX_FAKTEN_DIGEST}): " + fakten.joinToString("; "))
        }

        if (save.roomId == GameSave.ORT_DRAUSSEN) {
            val wo = currentParcel?.let { " in ${it.id} ${it.name}" }.orEmpty()
            sb.append("SPIELER: draußen$wo, Kachel(${save.ptx},${save.pty})")
        } else {
            sb.append("SPIELER: ${save.roomId} Kachel(${save.ptx},${save.pty})")
        }
        return sb.toString()
    }

    /** Eine knappe Zeile pro Parzelle: Raster, Gebäude (Ziel/Schloss), Requisiten, Figuren. */
    fun parcelLine(parcel: Parcel, outdoor: OutdoorMap): String {
        val gebaeude = parcel.buildingIds.mapNotNull { outdoor.buildings[it] }.joinToString("; ") { b ->
            val lock = if (b.locked) ":zu" + (b.lockHint?.let { "(\"$it\")" } ?: "") else ""
            "${b.id} ${b.name}$lock→${b.targetRoomId ?: "?"}"
        }
        val props = parcel.props.joinToString(" ") { prop ->
            val zustaende = prop.states.filterValues { it }.keys.map { if (it == "verschlossen") "zu" else it }
            val inhalt = prop.itemId?.let { "enthält $it" }
            val details = (zustaende + listOfNotNull(inhalt)).joinToString(",")
            "${prop.id} ${prop.name}" + (if (details.isBlank()) "" else "($details)")
        }
        val npcs = if (parcel.npcIds.isEmpty()) "" else " N[${parcel.npcIds.joinToString(" ")}]"
        return "${parcel.id} ${parcel.name} (${parcel.gx},${parcel.gy}) ${parcel.palette.name.lowercase()}" +
            (if (gebaeude.isBlank()) "" else " G[$gebaeude]") +
            (if (props.isBlank()) "" else " P[$props]") +
            npcs
    }

    /** Eine knappe Zeile pro Raum: Türen, Requisiten (Zustand, Inhalt), Figuren. */
    fun roomLine(room: Room): String {
        val doors = room.doors.joinToString("; ") { door ->
            val lock = if (door.locked) ":zu" + (door.lockHint?.let { "(\"$it\")" } ?: "") else ""
            "${door.wall.name}$lock→${door.targetRoomId ?: "?"}"
        }
        val props = room.props.joinToString(" ") { prop ->
            val zustaende = prop.states.filterValues { it }.keys.map { if (it == "verschlossen") "zu" else it }
            val inhalt = prop.itemId?.let { "enthält $it" }
            val details = (zustaende + listOfNotNull(inhalt)).joinToString(",")
            "${prop.id} ${prop.name}" + (if (details.isBlank()) "" else "($details)")
        }
        val npcs = if (room.npcIds.isEmpty()) "" else " N[${room.npcIds.joinToString(" ")}]"
        return "${room.id} ${room.name} ${room.w}x${room.h} ${room.palette.name.lowercase()}" +
            " T[$doors]" +
            (if (props.isBlank()) "" else " P[$props]") +
            npcs
    }
}
