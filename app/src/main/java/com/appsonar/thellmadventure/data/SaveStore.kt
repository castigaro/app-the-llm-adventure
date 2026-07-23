package com.appsonar.thellmadventure.data

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * JSON-Datei-Speicher für die Spielstände — gleiche Philosophie wie bei den
 * anderen AppSonar-Apps (schlanker als eine Datenbank), aber EINE DATEI PRO
 * SPIELSTAND: die KI-generierten Welten wachsen auf hunderte Kilobyte, und
 * der Autosave nach jeder Aktion soll nur den eigenen Stand schreiben.
 * Geschrieben wird atomar (Temp-Datei + Umbenennen).
 */
object SaveStore {

    private var cache: MutableMap<String, GameSave>? = null

    /** Nur für Tests: verwirft den In-Memory-Cache, erzwingt Neuladen. */
    internal fun resetForTest() {
        cache = null
    }

    private fun dir(context: Context): File =
        File(context.filesDir, "saves").apply { mkdirs() }

    internal fun fileFor(context: Context, id: String): File =
        File(dir(context), "$id.json")

    private fun load(context: Context): MutableMap<String, GameSave> {
        cache?.let { return it }
        val loaded = LinkedHashMap<String, GameSave>()
        dir(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }?.forEach { f ->
            runCatching {
                val save = GameSave.fromJson(JSONObject(f.readText()))
                loaded[save.id] = save
            }
        }
        cache = loaded
        return loaded
    }

    private fun write(context: Context, save: GameSave) {
        val target = fileFor(context, save.id)
        val tmp = File(target.parentFile, "${target.name}.tmp")
        tmp.writeText(save.toJson().toString())
        if (target.exists()) target.delete()
        tmp.renameTo(target)
    }

    fun getAll(context: Context): List<GameSave> =
        load(context).values.sortedByDescending { it.updatedAt }

    fun get(context: Context, id: String): GameSave? = load(context)[id]

    fun add(context: Context, save: GameSave) {
        load(context)[save.id] = save
        write(context, save)
    }

    /** Schreibt den Stand mit frischem Zeitstempel weg — nur seine eigene Datei. */
    fun update(context: Context, save: GameSave) {
        save.updatedAt = System.currentTimeMillis()
        load(context)[save.id] = save
        write(context, save)
    }

    fun delete(context: Context, save: GameSave) {
        load(context).remove(save.id)
        fileFor(context, save.id).delete()
    }

    /** Der jüngste Spielstand — für „Weiterspielen". */
    fun newest(context: Context): GameSave? = getAll(context).firstOrNull()
}
