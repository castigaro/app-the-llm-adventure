package com.appsonar.thellmadventure.api

import com.appsonar.thellmadventure.world.BodyShape
import com.appsonar.thellmadventure.world.BuildingStyle
import com.appsonar.thellmadventure.world.Chord
import com.appsonar.thellmadventure.world.Headgear
import com.appsonar.thellmadventure.world.Palette
import com.appsonar.thellmadventure.world.Timbre
import com.appsonar.thellmadventure.world.Wall
import org.json.JSONArray
import org.json.JSONObject

/**
 * JSON-Schemas für Structured Outputs (Anthropic output_config.format /
 * OpenAI response_format). Bewusst ohne numerische Grenzen (min/max) —
 * die unterstützen die APIs nicht; alle Clamps macht der ContentValidator.
 * Feldnamen deutsch, damit Prompt und Schema dieselbe Sprache sprechen.
 */
object Schemas {

    // ---- Bausteine -----------------------------------------------------------

    private fun obj(vararg props: Pair<String, JSONObject>): JSONObject = JSONObject().apply {
        put("type", "object")
        put("additionalProperties", false)
        put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
        put("required", JSONArray().apply { props.forEach { (k, _) -> put(k) } })
    }

    private fun str(): JSONObject = JSONObject().put("type", "string")

    private fun int(): JSONObject = JSONObject().put("type", "integer")

    private fun bool(): JSONObject = JSONObject().put("type", "boolean")

    private fun enumStr(values: List<String>): JSONObject = JSONObject().apply {
        put("type", "string")
        put("enum", JSONArray().apply { values.forEach { put(it.lowercase()) } })
    }

    private fun arr(items: JSONObject): JSONObject = JSONObject().apply {
        put("type", "array")
        put("items", items)
    }

    private fun nullable(schema: JSONObject): JSONObject = JSONObject().apply {
        put("anyOf", JSONArray().put(schema).put(JSONObject().put("type", "null")))
    }

    private inline fun <reified T : Enum<T>> enumOf(): JSONObject =
        enumStr(enumValues<T>().map { it.name })

    // ---- Inhalts-Schemas -----------------------------------------------------

    // Große Enums (IconArchetype 28, IconMaterial 8, PropType 30) stehen im
    // Schema als freie Strings: Die Anthropic-API lehnt zu große kompilierte
    // Grammatiken mit HTTP 400 ab („compiled grammar is too large"). Die
    // erlaubten Werte listet der Katalogtext im Prompt; erzwungen werden sie
    // ohnehin vom ContentValidator (unbekannt → verworfen/abgeleitet).
    private fun icon(): JSONObject = obj(
        "motiv" to str(),
        "material" to str(),
        "farbe" to int(),
        "leuchtet" to bool(),
        "abgenutzt" to bool(),
    )

    fun item(): JSONObject = obj(
        "name" to str(),
        "beschreibung" to str(),
        "icon" to icon(),
    )

    private fun aussehen(): JSONObject = obj(
        "koerper" to enumOf<BodyShape>(),
        "farbeA" to int(),
        "farbeB" to int(),
        "kopf" to enumOf<Headgear>(),
    )

    private fun zustaende(): JSONObject = obj(
        "offen" to bool(),
        "an" to bool(),
        "kaputt" to bool(),
        "verschlossen" to bool(),
    )

    private fun requisite(): JSONObject = obj(
        "typ" to str(), // PropType — als String, siehe Kommentar bei icon()
        "tx" to int(),
        "ty" to int(),
        "name" to str(),
        "farbe" to int(),
        "zustaende" to zustaende(),
        "blockiert" to bool(),
        "enthaeltItem" to nullable(item()),
    )

    private fun tuer(): JSONObject = obj(
        "wand" to enumOf<Wall>(),
        "pos" to int(),
        "verschlossen" to bool(),
        "hinweis" to nullable(str()),
        "name" to str(),
    )

    private fun figur(): JSONObject = obj(
        "name" to str(),
        "rolle" to str(),
        "charakterkarte" to str(),
        "aussehen" to aussehen(),
        "tx" to int(),
        "ty" to int(),
    )

    fun raum(): JSONObject = obj(
        "name" to str(),
        "breite" to int(),
        "hoehe" to int(),
        "palette" to enumOf<Palette>(),
        "tueren" to arr(tuer()),
        "requisiten" to arr(requisite()),
        "figuren" to arr(figur()),
    )

    private fun gebaeude(): JSONObject = obj(
        "name" to str(),
        "tx" to int(),
        "ty" to int(),
        "breite" to int(),
        "tiefe" to int(),
        "stil" to enumOf<BuildingStyle>(),
        "farbe" to int(),
        "verschlossen" to bool(),
        "hinweis" to nullable(str()),
    )

    /** Eine 12×12-Außenparzelle; gelaende = 12 Zeilen à 12 Geländezeichen. */
    fun parzelle(): JSONObject = obj(
        "name" to str(),
        "palette" to enumOf<Palette>(),
        "gelaende" to arr(str()),
        "gebaeude" to arr(gebaeude()),
        "requisiten" to arr(requisite()),
        "figuren" to arr(figur()),
    )

    fun melodie(): JSONObject = obj(
        "tempo" to int(),
        "klang" to enumOf<Timbre>(),
        "akkorde" to arr(enumOf<Chord>()),
        "bass" to arr(int()),
        "melodie" to arr(arr(int())),
    )

    /** Antwort der Story-Synthese beim Spielstart. */
    fun storySynthese(): JSONObject = obj(
        "titel" to str(),
        "praemisse" to str(),
        "ton" to str(),
        "zielItem" to item(),
        "zielUmschreibung" to str(),
        "spielerAussehen" to aussehen(),
        "startInventar" to arr(item()),
        "startgebiet" to parzelle(),
    )

    /** Antwort der Raum-Generierung beim Türdurchgang. */
    fun raumGenerierung(): JSONObject = obj(
        "raum" to raum(),
        "fakt" to str(),
        "zielHier" to bool(),
        "musikNeu" to nullable(melodie()),
    )

    /** Antwort der Parzellen-Generierung beim Aufdecken eines Nebelrands. */
    fun parzellenGenerierung(): JSONObject = obj(
        "parzelle" to parzelle(),
        "fakt" to str(),
        "zielHier" to bool(),
        "musikNeu" to nullable(melodie()),
    )

    /** Antwort eines NPC-Gesprächszugs. */
    fun dialog(): JSONObject = obj(
        "sag" to str(),
        "optionen" to arr(str()),
        "effekte" to arr(str()),
        "neues_item" to nullable(item()),
    )

    /** Urteil einer Objekt-Interaktion. */
    fun mutation(): JSONObject = obj(
        "text" to str(),
        "erfolg" to bool(),
        "effekte" to arr(str()),
    )

    /** Urteil einer Crafting-Kombination. */
    fun craft(): JSONObject = obj(
        "ergebnis" to enumStr(listOf("neu", "unmoeglich")),
        "text" to str(),
        "item" to nullable(item()),
    )
}
