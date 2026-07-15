package com.castigaro.thellmadventure.api

import android.content.Context
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.game.Effect
import com.castigaro.thellmadventure.game.StateDigest
import com.castigaro.thellmadventure.gen.WhitelistCatalog
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Prop
import org.json.JSONObject

/**
 * Objekt-Interaktions-Schiedsrichter: „Benutze [Gegenstand] mit [Requisite]"
 * oder „Benutze [Requisite]". Ist die Interaktion neu, urteilt die KI EINMAL,
 * das Ergebnis wird als Fakt und im mutationCache manifestiert — jede
 * Wiederholung läuft danach deterministisch ohne API-Aufruf.
 */
object MutationApi {

    class Result(
        val text: String,
        val erfolg: Boolean,
        val effekte: List<Effect>,
        /** true, wenn das Ergebnis aus dem Cache kam (keine Effekte mehr anwenden). */
        val gecacht: Boolean,
    )

    private fun cacheKey(prop: Prop, withItem: Item?): String = "${prop.id}|${withItem?.id ?: "-"}"

    /**
     * Führt die Interaktion aus. [ortId] ist der Ort der Requisite (Raum-Id
     * oder [com.castigaro.thellmadventure.data.GameSave.ORT_DRAUSSEN]).
     * Cache-Treffer → sofortiges Ergebnis ohne Effekte (die manifestierten
     * schon). Sonst ein KI-Aufruf; das Ergebnis wird gecacht.
     */
    fun interact(context: Context, save: GameSave, ortId: String, prop: Prop, withItem: Item?): Result {
        val key = cacheKey(prop, withItem)
        save.world.mutationCache[key]?.let { cached ->
            return Result(cached, erfolg = true, effekte = emptyList(), gecacht = true)
        }

        val erlaubt = WhitelistCatalog.forMutation(save, ortId, prop, withItem)
        val system = buildString {
            appendLine(
                "Du bist der Spielleiter eines deutschsprachigen Adventures und beurteilst EINE " +
                    "Objekt-Interaktion. Antworte NUR mit JSON: " +
                    "{\"text\": \"…\", \"erfolg\": true/false, \"effekte\": [\"…\"]}."
            )
            appendLine(StateDigest.build(save))
            appendLine()
            appendLine("REGELN:")
            appendLine("- text: 1-2 Sätze, was geschieht (auch bei Misserfolg atmosphärisch).")
            appendLine("- effekte: nur IDs aus dieser Liste (leer erlaubt): ${erlaubt.joinToString(", ")}")
            append("  fakt:<text> für einen Merksatz ist immer erlaubt. Bleib im Rahmen der Story.")
        }
        val aktion = if (withItem != null) {
            "Der Spieler benutzt „${withItem.name}“ (${withItem.id}) mit „${prop.name}“ (${prop.id})."
        } else {
            "Der Spieler benutzt „${prop.name}“ (${prop.id})."
        }
        val turns = listOf(LlmClient.Turn(LlmClient.Turn.ROLE_USER, aktion))
        val raw = LlmClient.chat(context, system, turns, maxTokens = 1024, schema = Schemas.mutation())
        val json = LlmClient.extractJson(raw)

        val text = json.optString("text").ifBlank { "Nichts geschieht." }.take(300)
        val erfolg = json.optBoolean("erfolg", false)
        val gemeldet = mutableListOf<String>()
        json.optJSONArray("effekte")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(gemeldet::add)
        }
        val effekte = WhitelistCatalog.resolve(save, gemeldet, erlaubt.toSet())

        // Erfolgreiche Interaktionen manifestieren (Cache + Fakt).
        if (erfolg) {
            save.world.mutationCache[key] = text
            save.world.addFact("Interaktion $key: ${text.take(80)}")
        }
        return Result(text, erfolg, effekte, gecacht = false)
    }
}
