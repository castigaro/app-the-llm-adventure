package com.castigaro.thellmadventure.data

import com.castigaro.thellmadventure.world.Look
import com.castigaro.thellmadventure.world.WorldState
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Ein Beitrag im Gesprächsverlauf mit einer Spielfigur. */
class DialogMessage(
    val role: String, // ROLE_PLAYER oder ROLE_NPC
    val text: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        put("text", text)
    }

    companion object {
        const val ROLE_PLAYER = "player"
        const val ROLE_NPC = "npc"

        fun fromJson(json: JSONObject) = DialogMessage(
            role = json.optString("role", ROLE_PLAYER),
            text = json.optString("text"),
        )
    }
}

/**
 * Ein kompletter Spielstand: die manifestierte Welt plus Spielerposition,
 * Aussehen, Gesprächsverläufe und Metadaten. Anders als bei Kap-Krakenzahn
 * gibt es keinen Seed — die von der KI erschaffene Welt selbst wird
 * gespeichert, denn sie ist aus nichts rekonstruierbar.
 */
class GameSave(
    val id: String = UUID.randomUUID().toString(),
    var name: String,
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var playtimeMs: Long = 0L,
    val world: WorldState = WorldState(),
    var roomId: String = "",
    var ptx: Int = 1,
    var pty: Int = 1,
    val playerLook: Look = Look(),
    val dialogHistories: MutableMap<String, MutableList<DialogMessage>> = mutableMapOf(),
    var finished: Boolean = false,
    // Einmaliger Hinweis, sobald die Welt groß wird (Anfragen wachsen mit).
    var growthHintShown: Boolean = false,
) {

    fun historyFor(npcId: String): MutableList<DialogMessage> =
        dialogHistories.getOrPut(npcId) { mutableListOf() }

    /** Tiefe Kopie mit neuer Id — Grundlage für manuelle Spielstands-Klone. */
    fun cloneAs(newName: String): GameSave {
        val copy = fromJson(toJson())
        return GameSave(
            id = UUID.randomUUID().toString(),
            name = newName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            playtimeMs = copy.playtimeMs,
            world = copy.world,
            roomId = copy.roomId,
            ptx = copy.ptx,
            pty = copy.pty,
            playerLook = copy.playerLook,
            dialogHistories = copy.dialogHistories,
            finished = copy.finished,
            growthHintShown = copy.growthHintShown,
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("playtimeMs", playtimeMs)
        put("world", world.toJson())
        put("roomId", roomId)
        put("ptx", ptx)
        put("pty", pty)
        put("playerLook", playerLook.toJson())
        put("dialogHistories", JSONObject().also { obj ->
            dialogHistories.forEach { (npcId, messages) ->
                obj.put(npcId, JSONArray().also { arr -> messages.forEach { arr.put(it.toJson()) } })
            }
        })
        put("finished", finished)
        put("growthHintShown", growthHintShown)
    }

    companion object {
        fun fromJson(json: JSONObject): GameSave {
            val save = GameSave(
                id = json.optString("id", UUID.randomUUID().toString()),
                name = json.optString("name"),
                createdAt = json.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
                playtimeMs = json.optLong("playtimeMs", 0L),
                world = json.optJSONObject("world")?.let { WorldState.fromJson(it) } ?: WorldState(),
                roomId = json.optString("roomId"),
                ptx = json.optInt("ptx", 1),
                pty = json.optInt("pty", 1),
                playerLook = Look.fromJson(json.optJSONObject("playerLook")),
                finished = json.optBoolean("finished", false),
                growthHintShown = json.optBoolean("growthHintShown", false),
            )
            json.optJSONObject("dialogHistories")?.let { obj ->
                obj.keys().forEach { npcId ->
                    val arr = obj.optJSONArray(npcId) ?: return@forEach
                    val messages = save.historyFor(npcId)
                    for (i in 0 until arr.length()) {
                        arr.optJSONObject(i)?.let { messages.add(DialogMessage.fromJson(it)) }
                    }
                }
            }
            return save
        }
    }
}
