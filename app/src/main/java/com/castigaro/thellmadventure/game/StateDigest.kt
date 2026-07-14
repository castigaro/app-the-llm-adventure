package com.castigaro.thellmadventure.game

import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.world.Limits
import com.castigaro.thellmadventure.world.Room

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

        // Räume: aktueller + angrenzende in voller Zeile, Rest nur namentlich
        val currentRoom = world.room(save.roomId)
        val detailIds = buildSet {
            currentRoom?.let { room ->
                add(room.id)
                room.doors.mapNotNull { it.targetRoomId }.forEach { add(it) }
            }
        }
        sb.appendLine("RÄUME:")
        world.rooms.values.filter { it.id in detailIds }.forEach { sb.appendLine(roomLine(it)) }
        val ferne = world.rooms.values.filter { it.id !in detailIds }
        if (ferne.isNotEmpty()) {
            sb.appendLine("(weitere: ${ferne.joinToString(", ") { "${it.id} ${it.name}" }})")
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

        sb.append("SPIELER: ${save.roomId} Kachel(${save.ptx},${save.pty})")
        return sb.toString()
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
