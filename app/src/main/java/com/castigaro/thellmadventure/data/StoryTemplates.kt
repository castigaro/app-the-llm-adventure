package com.castigaro.thellmadventure.data

import android.content.Context
import com.castigaro.thellmadventure.world.Room
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * „Als neue Geschichte sichern": Ein Schnappschuss der aktuell manifestierten
 * Welt wird zu einer neuen, wiederspielbaren Startvorlage. Die Vorlage behält
 * die Fakten (das Gedächtnis der Welt) und den bisherigen Aufbau, verwirft
 * aber Gesprächsverläufe und Spielzeit — beim Start aus einer Vorlage
 * überspringt das Spiel die Intake-Fragen und Story-Synthese; unentdeckte
 * Türen generieren weiterhin frisch, sodass sich Wiederholungen unterscheiden.
 *
 * Vorlagen sind klein und listenförmig — hier genügt EINE Datei.
 */
object StoryTemplates {

    /** Eine gespeicherte Vorlage: Name, Zeitstempel und der bereinigte Spielstand. */
    class Template(
        val id: String,
        val name: String,
        val savedAt: Long,
        val snapshot: JSONObject,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("id", id)
            put("name", name)
            put("savedAt", savedAt)
            put("snapshot", snapshot)
        }

        companion object {
            fun fromJson(json: JSONObject): Template? {
                val snapshot = json.optJSONObject("snapshot") ?: return null
                return Template(
                    id = json.optString("id", UUID.randomUUID().toString()),
                    name = json.optString("name", "Vorlage"),
                    savedAt = json.optLong("savedAt", System.currentTimeMillis()),
                    snapshot = snapshot,
                )
            }
        }
    }

    private var cache: MutableList<Template>? = null

    internal fun resetForTest() {
        cache = null
    }

    private fun file(context: Context): File = File(context.filesDir, "templates.json")

    private fun load(context: Context): MutableList<Template> {
        cache?.let { return it }
        val loaded = mutableListOf<Template>()
        val f = file(context)
        if (f.exists()) {
            runCatching {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { Template.fromJson(it)?.let(loaded::add) }
                }
            }
        }
        cache = loaded
        return loaded
    }

    private fun persist(context: Context) {
        val arr = JSONArray()
        load(context).forEach { arr.put(it.toJson()) }
        val target = file(context)
        val tmp = File(target.parentFile, "templates.json.tmp")
        tmp.writeText(arr.toString())
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    fun getAll(context: Context): List<Template> =
        load(context).sortedByDescending { it.savedAt }

    fun delete(context: Context, template: Template) {
        load(context).removeAll { it.id == template.id }
        persist(context)
    }

    /** Erzeugt aus dem laufenden Spielstand eine bereinigte Vorlage. */
    fun saveFrom(context: Context, save: GameSave, name: String): Template {
        val snapshot = cleanSnapshot(save)
        val template = Template(UUID.randomUUID().toString(), name, System.currentTimeMillis(), snapshot)
        load(context).add(template)
        persist(context)
        return template
    }

    /** Startet eine neue Partie aus einer Vorlage (frische Id, frische Spielzeit). */
    fun newSaveFrom(template: Template, name: String): GameSave {
        val base = GameSave.fromJson(template.snapshot)
        return GameSave(
            id = UUID.randomUUID().toString(),
            name = name,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            playtimeMs = 0L,
            world = base.world,
            roomId = base.roomId,
            ptx = base.ptx,
            pty = base.pty,
            playerLook = base.playerLook,
            finished = false,
        )
    }

    /**
     * Tiefe Kopie über JSON, dann bereinigt: Gesprächsverläufe raus,
     * Spielzeit auf 0, nicht beendet, Spieler auf eine begehbare Startkachel.
     */
    private fun cleanSnapshot(save: GameSave): JSONObject {
        val copy = GameSave.fromJson(save.toJson())
        copy.dialogHistories.clear()
        copy.playtimeMs = 0L
        copy.finished = false
        val room = copy.world.room(copy.roomId)
        if (room != null) {
            val tile = startTile(room)
            copy.ptx = tile.first
            copy.pty = tile.second
        }
        return copy.toJson()
    }

    private fun startTile(room: Room): Pair<Int, Int> {
        for (ty in 0 until room.h) {
            for (tx in 0 until room.w) {
                if (room.walkable(tx, ty) && room.doorAt(tx, ty) == null) return tx to ty
            }
        }
        return room.w / 2 to room.h / 2
    }
}
