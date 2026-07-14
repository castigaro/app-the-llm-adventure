package com.castigaro.thellmadventure.api

import android.content.Context
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.gen.ContentValidator
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Recipe
import org.json.JSONObject

/**
 * Crafting-Schiedsrichter: 2–3 Gegenstände → 1 neuer. Eine Kombination wird
 * EINMAL von der KI beurteilt; das Ergebnis (neuer Gegenstand oder
 * „unmöglich") wird als Rezept-Fakt gecacht und ist danach fix — ein
 * Cache-Treffer läuft ganz ohne API-Aufruf. Erst bei Erfolg werden die
 * Zutaten verbraucht.
 */
object CraftApi {

    class Result(
        val erfolg: Boolean,
        val text: String,
        /** Der gecraftete Gegenstand (nur bei Erfolg), bereits im Inventar. */
        val neuesItem: Item?,
    )

    /**
     * Verbindet die Gegenstände mit den [inputIds] (2–3, aus dem Inventar).
     * Cache-Treffer liefert sofort; sonst ein KI-Aufruf. Bei Erfolg werden
     * die Zutaten verbraucht und der neue Gegenstand ins Inventar gelegt.
     */
    fun craft(context: Context, save: GameSave, inputIds: List<String>): Result {
        val world = save.world
        val inputs = inputIds.mapNotNull { world.items[it] }
            .filter { it.holder == Item.HOLDER_INVENTORY }
        if (inputs.size < 2) {
            return Result(false, "Dafür brauchst du mindestens zwei Gegenstände.", null)
        }

        // Cache-Treffer?
        world.recipeFor(inputs.map { it.id })?.let { recipe ->
            return replay(save, recipe, inputs)
        }

        val system = buildString {
            appendLine(
                "Du bist der Spielleiter eines deutschsprachigen Adventures und beurteilst, ob sich " +
                    "Gegenstände sinnvoll VERBINDEN lassen. Antworte NUR mit JSON: " +
                    "{\"ergebnis\": \"neu\"|\"unmoeglich\", \"text\": \"…\", \"item\": {…}}. " +
                    "Das Feld item nur bei ergebnis=neu."
            )
            appendLine(com.castigaro.thellmadventure.game.StateDigest.build(save))
            appendLine()
            appendLine("REGELN:")
            appendLine("- Nur verbinden, was in DIESER Welt plausibel ein neues, nützliches Ding ergibt.")
            appendLine("- item: der neue Gegenstand (name, beschreibung, icon aus dem Katalog).")
            append("- Im Zweifel ergebnis=unmoeglich mit einem kurzen, atmosphärischen text.")
        }
        val zutaten = inputs.joinToString(", ") { "${it.name} (${it.id})" }
        val turns = listOf(LlmClient.Turn(LlmClient.Turn.ROLE_USER, "Der Spieler verbindet: $zutaten."))
        val raw = LlmClient.chat(context, system, turns, maxTokens = 1024, schema = Schemas.craft())
        val json = LlmClient.extractJson(raw)

        val erfolg = json.optString("ergebnis").equals("neu", ignoreCase = true)
        val text = json.optString("text").ifBlank {
            if (erfolg) "Die Teile fügen sich zusammen." else "Das passt nicht zusammen."
        }.take(200)

        if (!erfolg) {
            // „unmöglich" als fixer Fakt cachen — künftig ohne API-Aufruf.
            world.recipes.add(Recipe(world.nextId("c"), inputs.map { it.id }.sorted(), null, text))
            world.addFact("Kombination ${inputs.joinToString("+") { it.id }} → unmöglich")
            return Result(false, text, null)
        }

        val neu = json.optJSONObject("item")?.let { ContentValidator.buildItem(it, world) }
            ?: return Result(false, "Das passt nicht zusammen.", null)
        world.addItem(neu)
        world.recipes.add(Recipe(world.nextId("c"), inputs.map { it.id }.sorted(), neu.id, text))

        applyCraft(save, inputs, neu)
        return Result(true, text, neu)
    }

    /** Wiederholte bekannte Kombination: kein API-Aufruf. */
    private fun replay(save: GameSave, recipe: Recipe, inputs: List<Item>): Result {
        if (recipe.outputItemId == null) {
            return Result(false, recipe.text.ifBlank { "Das passt nicht zusammen." }, null)
        }
        // Für ein bekanntes Rezept einen frischen Ergebnis-Gegenstand erzeugen.
        val vorlage = save.world.items[recipe.outputItemId]
            ?: return Result(false, "Das passt nicht zusammen.", null)
        val neu = Item(
            id = save.world.nextId("i"),
            name = vorlage.name,
            beschreibung = vorlage.beschreibung,
            icon = vorlage.icon,
        )
        save.world.addItem(neu)
        applyCraft(save, inputs, neu)
        return Result(true, recipe.text.ifBlank { "Vertraute Teile, vertrautes Ergebnis." }, neu)
    }

    /** Zutaten verbrauchen, Ergebnis ins Inventar, Fakt schreiben. */
    private fun applyCraft(save: GameSave, inputs: List<Item>, ergebnis: Item) {
        inputs.forEach { it.holder = Item.HOLDER_CONSUMED }
        ergebnis.holder = Item.HOLDER_INVENTORY
        save.world.addFact("${ergebnis.id} gecraftet aus ${inputs.joinToString("+") { it.id }}")
    }
}
