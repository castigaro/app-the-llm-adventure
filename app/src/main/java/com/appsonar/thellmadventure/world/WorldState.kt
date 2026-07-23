package com.appsonar.thellmadventure.world

import org.json.JSONArray
import org.json.JSONObject

/**
 * Der wachsende, manifestierte Weltzustand eines Durchlaufs: alles, was die
 * KI je erschaffen hat, plus die Faktenliste als Gedächtnis. Einmal Erzeugtes
 * bleibt bis zum Spielende bestehen (Manifestierung) — nur einzelne
 * Eigenschaften (Zustände, Aufenthaltsorte) ändern sich.
 *
 * Die ID-Vergabe läuft über Zähler pro Präfix (r=Raum, p=Requisite, n=Figur,
 * i=Gegenstand, c=Rezept, s=Fähigkeit, m=Melodie, f=Fakt), damit IDs auch
 * nach Laden eindeutig bleiben.
 */
class WorldState {

    val rooms = LinkedHashMap<String, Room>()
    val npcs = LinkedHashMap<String, Npc>()
    val items = LinkedHashMap<String, Item>()
    val recipes = mutableListOf<Recipe>()
    val skills = LinkedHashMap<String, Skill>()
    val melodies = LinkedHashMap<String, MelodySpec>()

    /** Die Außenkarte; null nur bei Spielständen aus der Zeit vor der Außenwelt. */
    var outdoor: OutdoorMap? = null

    /** Knappe Fakt-Strings inkl. ID, z. B. "f13 p2 aktiviert". Append-only. */
    val facts = mutableListOf<String>()

    /**
     * Gecachte Ergebnisse von Objekt-Interaktionen: Schlüssel "propId|itemId"
     * → Ergebnistext. Einmal beurteilt, wird die Interaktion deterministisch
     * wiederholt (kein weiterer KI-Aufruf, keine erneuten Effekte).
     */
    val mutationCache = LinkedHashMap<String, String>()

    var story: Story? = null

    private val counters = HashMap<String, Int>()

    /** Nächste freie Kurz-ID für ein Präfix, z. B. nextId("r") → "r5". */
    fun nextId(prefix: String): String {
        val next = (counters[prefix] ?: 0) + 1
        counters[prefix] = next
        return "$prefix$next"
    }

    /** Hängt einen Fakt an und gibt ihn inklusive vergebener ID zurück. */
    fun addFact(text: String): String {
        val fact = "${nextId("f")} $text"
        facts.add(fact)
        return fact
    }

    fun room(id: String): Room? = rooms[id]

    fun addRoom(room: Room) {
        rooms[room.id] = room
    }

    fun addItem(item: Item) {
        items[item.id] = item
    }

    fun addNpc(npc: Npc) {
        npcs[npc.id] = npc
    }

    fun recipeFor(inputs: Collection<String>): Recipe? {
        val key = Recipe.key(inputs)
        return recipes.firstOrNull { Recipe.key(it.inputs) == key }
    }

    /** Gegenstände im Spieler-Inventar, in Erwerbsreihenfolge. */
    fun inventory(): List<Item> = items.values.filter { it.holder == Item.HOLDER_INVENTORY }

    /** Alle Requisiten der Welt: Innenräume UND Außenparzellen. */
    fun allProps(): Sequence<Prop> =
        rooms.values.asSequence().flatMap { it.props.asSequence() } +
            (outdoor?.allProps() ?: emptySequence())

    /** Requisiten eines Orts (Raum-Id oder Parzellen-Id). */
    fun ortProps(ortId: String): List<Prop> =
        rooms[ortId]?.props ?: outdoor?.parcels?.get(ortId)?.props ?: emptyList()

    /** Anzeigename eines Orts (Raum oder Parzelle), null wenn unbekannt. */
    fun ortName(ortId: String): String? =
        rooms[ortId]?.name ?: outdoor?.parcels?.get(ortId)?.name

    /** Zahl aller Orte (Innenräume + Parzellen) — Grundlage fürs Ziel-Pacing. */
    fun orteCount(): Int = rooms.size + (outdoor?.parcels?.size ?: 0)

    /** Die Requisite, die einen Gegenstand birgt (oder null). */
    fun propHolding(itemId: String): Prop? {
        val item = items[itemId] ?: return null
        if (!item.holder.startsWith("prop:")) return null
        val propId = item.holder.removePrefix("prop:")
        return allProps().firstOrNull { it.id == propId }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("rooms", JSONArray().also { arr -> rooms.values.forEach { arr.put(it.toJson()) } })
        put("npcs", JSONArray().also { arr -> npcs.values.forEach { arr.put(it.toJson()) } })
        put("items", JSONArray().also { arr -> items.values.forEach { arr.put(it.toJson()) } })
        put("recipes", JSONArray().also { arr -> recipes.forEach { arr.put(it.toJson()) } })
        put("skills", JSONArray().also { arr -> skills.values.forEach { arr.put(it.toJson()) } })
        put("melodies", JSONArray().also { arr -> melodies.values.forEach { arr.put(it.toJson()) } })
        put("facts", JSONArray().also { arr -> facts.forEach { arr.put(it) } })
        put("mutationCache", JSONObject().also { obj -> mutationCache.forEach { (k, v) -> obj.put(k, v) } })
        put("story", story?.toJson() ?: JSONObject.NULL)
        put("outdoor", outdoor?.toJson() ?: JSONObject.NULL)
        put("counters", JSONObject().also { obj -> counters.forEach { (k, v) -> obj.put(k, v) } })
    }

    companion object {
        fun fromJson(json: JSONObject): WorldState {
            val world = WorldState()
            json.optJSONArray("rooms")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { Room.fromJson(it)?.let(world::addRoom) }
                }
            }
            json.optJSONArray("npcs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { Npc.fromJson(it)?.let(world::addNpc) }
                }
            }
            json.optJSONArray("items")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { Item.fromJson(it)?.let(world::addItem) }
                }
            }
            json.optJSONArray("recipes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { Recipe.fromJson(it)?.let(world.recipes::add) }
                }
            }
            json.optJSONArray("skills")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { obj -> Skill.fromJson(obj)?.let { world.skills[it.id] = it } }
                }
            }
            json.optJSONArray("melodies")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { obj -> MelodySpec.fromJson(obj)?.let { world.melodies[it.id] = it } }
                }
            }
            json.optJSONArray("facts")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let(world.facts::add)
                }
            }
            json.optJSONObject("mutationCache")?.let { obj ->
                obj.keys().forEach { key -> world.mutationCache[key] = obj.optString(key) }
            }
            world.story = Story.fromJson(json.optJSONObject("story"))
            world.outdoor = OutdoorMap.fromJson(json.optJSONObject("outdoor"))
            json.optJSONObject("counters")?.let { obj ->
                obj.keys().forEach { key -> world.counters[key] = obj.optInt(key, 0) }
            }
            return world
        }
    }
}
