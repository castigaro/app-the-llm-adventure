package com.appsonar.thellmadventure.world

import com.appsonar.thellmadventure.data.enumOrNull
import org.json.JSONArray
import org.json.JSONObject

/**
 * Das dynamische Weltmodell. Anders als bei Kap-Krakenzahn (statische Welt,
 * nur der Seed wird gespeichert) IST hier die Welt die Daten: Räume,
 * Requisiten, Figuren, Gegenstände, Rezepte und Melodien entstehen zur
 * Laufzeit durch die KI, manifestieren sich und werden komplett mitgespeichert.
 *
 * Kurz-IDs (r1, p1, n1, i1, c1, s1, m1, …) halten Spielstand und KI-Prompts
 * kompakt. Alle Klassen serialisieren nach org.json mit tolerantem Parsing:
 * Unbekanntes wird verworfen oder auf Defaults gesetzt, nie geworfen.
 */

/**
 * Eine Tür in einer Raumwand. [targetRoomId] null bedeutet: der Raum dahinter
 * ist noch nicht entdeckt — er wird erst beim Durchschreiten von der KI
 * generiert und manifestiert sich dann dauerhaft.
 */
class Door(
    val wall: Wall,
    val pos: Int,
    var targetRoomId: String? = null,
    var locked: Boolean = false,
    var lockHint: String? = null,
    var label: String = "Tür",
) {
    /** Die begehbare Kachel dieser Tür im Raum [roomW] × [roomH]. */
    fun tile(roomW: Int, roomH: Int): Pair<Int, Int> = when (wall) {
        Wall.N -> pos to 0
        Wall.S -> pos to roomH - 1
        Wall.W -> 0 to pos
        Wall.O -> roomW - 1 to pos
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("wall", wall.name)
        put("pos", pos)
        put("target", targetRoomId ?: JSONObject.NULL)
        put("locked", locked)
        put("lockHint", lockHint ?: JSONObject.NULL)
        put("label", label)
    }

    companion object {
        fun fromJson(json: JSONObject): Door? {
            val wall = enumOrNull<Wall>(json.optString("wall")) ?: return null
            return Door(
                wall = wall,
                pos = json.optInt("pos", 0),
                targetRoomId = json.optString("target").takeIf { it.isNotBlank() && it != "null" },
                locked = json.optBoolean("locked", false),
                lockHint = json.optString("lockHint").takeIf { it.isNotBlank() && it != "null" },
                label = json.optString("label", "Tür"),
            )
        }
    }
}

/**
 * Eine Requisite auf einer Kachel. [itemId] verweist auf einen Gegenstand,
 * der hier liegt/enthalten ist (verschwindet beim Aufnehmen aus dem Raum).
 * [blocksTile] macht die Kachel für den Pfadfinder unpassierbar.
 */
class Prop(
    val id: String,
    var type: PropType,
    var tx: Int,
    var ty: Int,
    var name: String,
    var colorSlot: Int = 0,
    val states: MutableMap<String, Boolean> = mutableMapOf(),
    var itemId: String? = null,
    var blocksTile: Boolean = true,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("tx", tx)
        put("ty", ty)
        put("name", name)
        put("colorSlot", colorSlot)
        put("states", JSONObject().also { obj -> states.forEach { (k, v) -> obj.put(k, v) } })
        put("itemId", itemId ?: JSONObject.NULL)
        put("blocksTile", blocksTile)
    }

    companion object {
        fun fromJson(json: JSONObject): Prop? {
            val type = enumOrNull<PropType>(json.optString("type")) ?: return null
            val prop = Prop(
                id = json.optString("id"),
                type = type,
                tx = json.optInt("tx", 0),
                ty = json.optInt("ty", 0),
                name = json.optString("name", type.name.lowercase()),
                colorSlot = json.optInt("colorSlot", 0),
                itemId = json.optString("itemId").takeIf { it.isNotBlank() && it != "null" },
                blocksTile = json.optBoolean("blocksTile", true),
            )
            json.optJSONObject("states")?.let { obj ->
                obj.keys().forEach { key ->
                    if (key in PROP_STATES) prop.states[key] = obj.optBoolean(key, false)
                }
            }
            return prop
        }
    }
}

/** Das prozedurale Aussehen einer Figur — komplett aus dem Vokabular. */
class Look(
    var body: BodyShape = BodyShape.TRAPEZ,
    var colorA: Int = 0,
    var colorB: Int = 3,
    var headgear: Headgear = Headgear.KEINE,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("body", body.name)
        put("colorA", colorA)
        put("colorB", colorB)
        put("headgear", headgear.name)
    }

    companion object {
        fun fromJson(json: JSONObject?): Look {
            if (json == null) return Look()
            return Look(
                body = enumOrNull<BodyShape>(json.optString("body")) ?: BodyShape.TRAPEZ,
                colorA = json.optInt("colorA", 0),
                colorB = json.optInt("colorB", 3),
                headgear = enumOrNull<Headgear>(json.optString("headgear")) ?: Headgear.KEINE,
            )
        }
    }
}

/** Ein Raum: das Grundelement der Welt. Größe in Kacheln, Inhalt wächst. */
class Room(
    val id: String,
    var name: String,
    val w: Int,
    val h: Int,
    var palette: Palette = Palette.STAHL,
    val doors: MutableList<Door> = mutableListOf(),
    val props: MutableList<Prop> = mutableListOf(),
    val npcIds: MutableList<String> = mutableListOf(),
    var melodyId: String? = null,
) {
    fun propAt(tx: Int, ty: Int): Prop? = props.firstOrNull { it.tx == tx && it.ty == ty }

    fun doorAt(tx: Int, ty: Int): Door? = doors.firstOrNull { it.tile(w, h) == tx to ty }

    /** Begehbar = im Raum und nicht von einer blockierenden Requisite belegt. */
    fun walkable(tx: Int, ty: Int): Boolean =
        tx in 0 until w && ty in 0 until h && props.none { it.blocksTile && it.tx == tx && it.ty == ty }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("w", w)
        put("h", h)
        put("palette", palette.name)
        put("doors", JSONArray().also { arr -> doors.forEach { arr.put(it.toJson()) } })
        put("props", JSONArray().also { arr -> props.forEach { arr.put(it.toJson()) } })
        put("npcIds", JSONArray().also { arr -> npcIds.forEach { arr.put(it) } })
        put("melodyId", melodyId ?: JSONObject.NULL)
    }

    companion object {
        fun fromJson(json: JSONObject): Room? {
            val id = json.optString("id")
            if (id.isBlank()) return null
            val room = Room(
                id = id,
                name = json.optString("name", "Raum"),
                w = json.optInt("w", Limits.MIN_RAUM_W).coerceIn(Limits.MIN_RAUM_W, Limits.MAX_RAUM_W),
                h = json.optInt("h", Limits.MIN_RAUM_H).coerceIn(Limits.MIN_RAUM_H, Limits.MAX_RAUM_H),
                palette = enumOrNull<Palette>(json.optString("palette")) ?: Palette.STAHL,
                melodyId = json.optString("melodyId").takeIf { it.isNotBlank() && it != "null" },
            )
            json.optJSONArray("doors")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { Door.fromJson(it)?.let(room.doors::add) }
                }
            }
            json.optJSONArray("props")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { Prop.fromJson(it)?.let(room.props::add) }
                }
            }
            json.optJSONArray("npcIds")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let(room.npcIds::add)
                }
            }
            return room
        }
    }
}

/**
 * Prozedurales Icon eines Gegenstands oder einer Fähigkeit.
 *
 * Neue Icons tragen ein Detail-[motiv] samt [material]; [shape]/[glyph] bleiben
 * nur als Alt-Daten erhalten. [motivUndMaterial] löst beim Malen auf, was
 * tatsächlich gezeichnet wird — Alt-Spielstände brauchen so keine Migration.
 */
class IconSpec(
    var shape: IconShape = IconShape.WUERFEL,
    var colorIdx: Int = 0,
    var glyph: String = "",
    var motiv: IconArchetype? = null,
    var material: IconMaterial = IconMaterial.EISEN,
    var leuchtet: Boolean = false,
    var abgenutzt: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("shape", shape.name)
        put("colorIdx", colorIdx)
        put("glyph", glyph)
        motiv?.let { put("motiv", it.name) }
        put("material", material.name)
        put("leuchtet", leuchtet)
        put("abgenutzt", abgenutzt)
    }

    /**
     * Motiv und Material, die tatsächlich gemalt werden: explizites Motiv vor
     * Namens-Schlüsselwort vor Alt-Form-Zuordnung.
     */
    fun motivUndMaterial(name: String): Pair<IconArchetype, IconMaterial> {
        motiv?.let { return it to material }
        zuordnung(name)?.let { return it.motiv to it.material }
        return motivFromShape(shape)
    }

    /** Ein Schlüsselwort-Treffer der Namens-Ableitung. */
    private class Zuordnung(
        val woerter: List<String>,
        val motiv: IconArchetype,
        val material: IconMaterial,
    )

    companion object {
        fun fromJson(json: JSONObject?): IconSpec {
            if (json == null) return IconSpec()
            return IconSpec(
                shape = enumOrNull<IconShape>(json.optString("shape")) ?: IconShape.WUERFEL,
                colorIdx = json.optInt("colorIdx", 0),
                glyph = json.optString("glyph").take(1),
                motiv = enumOrNull<IconArchetype>(json.optString("motiv")),
                material = enumOrNull<IconMaterial>(json.optString("material"))
                    ?: IconMaterial.EISEN,
                leuchtet = json.optBoolean("leuchtet", false),
                abgenutzt = json.optBoolean("abgenutzt", false),
            )
        }

        /** Fallback: aus dem Namen deterministisch ein Icon ableiten. */
        fun derived(name: String): IconSpec {
            val hash = name.hashCode()
            val treffer = zuordnung(name)
            val motiv = treffer?.motiv
                ?: IconArchetype.values()[Math.floorMod(hash, IconArchetype.values().size)]
            return IconSpec(
                shape = IconShape.values()[Math.floorMod(hash, IconShape.values().size)],
                colorIdx = Math.floorMod(hash / 31, SLOT_COLORS.size),
                glyph = name.firstOrNull()?.uppercase() ?: "",
                motiv = motiv,
                material = treffer?.material
                    ?: IconMaterial.values()[Math.floorMod(hash / 7, IconMaterial.values().size)],
                leuchtet = motiv in LEUCHTENDE,
            )
        }

        /** Alt-Silhouette → nächstliegendes Detail-Motiv (für alte Spielstände). */
        fun motivFromShape(shape: IconShape): Pair<IconArchetype, IconMaterial> = when (shape) {
            IconShape.KREIS -> IconArchetype.MUENZE to IconMaterial.MESSING
            IconShape.RAUTE -> IconArchetype.KRISTALL to IconMaterial.KRISTALL
            IconShape.FLASCHE -> IconArchetype.FLASCHE to IconMaterial.GLAS
            IconShape.SCHLUESSEL -> IconArchetype.SCHLUESSEL to IconMaterial.MESSING
            IconShape.ZAHNRAD -> IconArchetype.ZAHNRAD to IconMaterial.EISEN
            IconShape.ROLLE -> IconArchetype.SCHRIFTROLLE to IconMaterial.KNOCHEN
            IconShape.STERN -> IconArchetype.AMULETT to IconMaterial.MESSING
            IconShape.TROPFEN -> IconArchetype.FLASCHE to IconMaterial.GLAS
            IconShape.SCHILD -> IconArchetype.AMULETT to IconMaterial.EISEN
            IconShape.WUERFEL -> IconArchetype.WUERFEL to IconMaterial.KNOCHEN
            IconShape.BLITZ -> IconArchetype.KRISTALL to IconMaterial.KRISTALL
            IconShape.HERZ -> IconArchetype.AMULETT to IconMaterial.STOFF
        }

        /** Motive, die von sich aus leuchten, wenn sie per Name abgeleitet werden. */
        private val LEUCHTENDE = setOf(
            IconArchetype.FACKEL, IconArchetype.LATERNE, IconArchetype.KRISTALL,
        )

        /**
         * Deutsche Schlüsselwörter → Motiv. Reihenfolge zählt: Spezifisches
         * steht vor Allgemeinem („Schraubenschlüssel" ist Werkzeug, kein
         * Schlüssel; „Edelstein" Kristall, kein Stein; „Zahnrad" kein Knochen).
         */
        private val ZUORDNUNGEN = listOf(
            Zuordnung(listOf("hammer", "zange", "schrauben", "werkzeug", "meißel", "meissel", "bohrer", "säge", "saege"), IconArchetype.WERKZEUG, IconMaterial.EISEN),
            Zuordnung(listOf("schlüssel", "schluessel"), IconArchetype.SCHLUESSEL, IconMaterial.MESSING),
            Zuordnung(listOf("zahnrad", "getriebe"), IconArchetype.ZAHNRAD, IconMaterial.EISEN),
            Zuordnung(listOf("kristall", "juwel", "edelstein", "diamant", "rubin", "smaragd", "saphir"), IconArchetype.KRISTALL, IconMaterial.KRISTALL),
            Zuordnung(listOf("flasche", "trank", "phiole", "elixier", "tinktur", "öl"), IconArchetype.FLASCHE, IconMaterial.GLAS),
            Zuordnung(listOf("schriftrolle", "pergament", "brief", "notiz", "zettel", "urkunde"), IconArchetype.SCHRIFTROLLE, IconMaterial.KNOCHEN),
            Zuordnung(listOf("buch", "grimoire", "fibel"), IconArchetype.BUCH, IconMaterial.STOFF),
            Zuordnung(listOf("schwert", "messer", "dolch", "klinge", "säbel", "saebel", "axt", "machete"), IconArchetype.KLINGE, IconMaterial.EISEN),
            Zuordnung(listOf("fackel"), IconArchetype.FACKEL, IconMaterial.HOLZ),
            Zuordnung(listOf("laterne", "lampe", "leuchte"), IconArchetype.LATERNE, IconMaterial.MESSING),
            Zuordnung(listOf("seil", "schnur", "kordel"), IconArchetype.SEIL, IconMaterial.HOLZ),
            Zuordnung(listOf("haken", "anker"), IconArchetype.HAKEN, IconMaterial.EISEN),
            Zuordnung(listOf("münze", "muenze", "taler", "dukate"), IconArchetype.MUENZE, IconMaterial.MESSING),
            Zuordnung(listOf("amulett", "medaillon", "anhänger", "anhaenger", "talisman"), IconArchetype.AMULETT, IconMaterial.MESSING),
            Zuordnung(listOf("kompass"), IconArchetype.KOMPASS, IconMaterial.MESSING),
            Zuordnung(listOf("karte", "plan", "atlas"), IconArchetype.KARTE, IconMaterial.KNOCHEN),
            Zuordnung(listOf("horn"), IconArchetype.HORN, IconMaterial.KNOCHEN),
            Zuordnung(listOf("feder"), IconArchetype.FEDER, IconMaterial.KNOCHEN),
            Zuordnung(listOf("pilz"), IconArchetype.PILZ, IconMaterial.STOFF),
            Zuordnung(listOf("kraut", "blume", "blüte", "bluete", "blatt", "pflanze", "wurzel", "moos"), IconArchetype.KRAUT, IconMaterial.HOLZ),
            Zuordnung(listOf("knochen", "schädel", "schaedel", "skelett", "zahn"), IconArchetype.KNOCHEN, IconMaterial.KNOCHEN),
            Zuordnung(listOf("beutel", "sack", "tasche"), IconArchetype.BEUTEL, IconMaterial.STOFF),
            Zuordnung(listOf("krug", "becher", "tasse", "kelch", "humpen", "kanne"), IconArchetype.KRUG, IconMaterial.HOLZ),
            Zuordnung(listOf("spiegel"), IconArchetype.SPIEGEL, IconMaterial.GLAS),
            Zuordnung(listOf("glocke"), IconArchetype.GLOCKE, IconMaterial.MESSING),
            Zuordnung(listOf("würfel", "wuerfel"), IconArchetype.WUERFEL, IconMaterial.KNOCHEN),
            Zuordnung(listOf("ring"), IconArchetype.RING, IconMaterial.MESSING),
            Zuordnung(listOf("stein", "fels", "kiesel", "brocken"), IconArchetype.STEIN, IconMaterial.STEIN),
        )

        private fun zuordnung(name: String): Zuordnung? {
            val klein = name.lowercase()
            return ZUORDNUNGEN.firstOrNull { z -> z.woerter.any { klein.contains(it) } }
        }
    }
}

/**
 * Ein Gegenstand. [holder] ist die einzige Wahrheit über den Aufenthaltsort:
 * "inv" (Inventar), "prop:<id>" (liegt in/auf einer Requisite),
 * "npc:<id>" (bei einer Figur), "verbraucht" (beim Craften aufgegangen).
 * Prop.itemId wird von der Engine synchron gehalten (fürs Funkeln im Raum).
 */
class Item(
    val id: String,
    var name: String,
    var beschreibung: String = "",
    val icon: IconSpec = IconSpec(),
    var holder: String = HOLDER_INVENTORY,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("beschreibung", beschreibung)
        put("icon", icon.toJson())
        put("holder", holder)
    }

    companion object {
        const val HOLDER_INVENTORY = "inv"
        const val HOLDER_CONSUMED = "verbraucht"

        fun holderProp(propId: String) = "prop:$propId"
        fun holderNpc(npcId: String) = "npc:$npcId"

        fun fromJson(json: JSONObject): Item? {
            val id = json.optString("id")
            if (id.isBlank()) return null
            return Item(
                id = id,
                name = json.optString("name", "Ding"),
                beschreibung = json.optString("beschreibung"),
                icon = IconSpec.fromJson(json.optJSONObject("icon")),
                holder = json.optString("holder", HOLDER_INVENTORY),
            )
        }
    }
}

/** Eine zufällig generierte Spielfigur mit Charakterkarte für die KI. */
class Npc(
    val id: String,
    var name: String,
    var roomId: String,
    var tx: Int,
    var ty: Int,
    val look: Look = Look(),
    var charakterkarte: String = "",
    var rolle: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("roomId", roomId)
        put("tx", tx)
        put("ty", ty)
        put("look", look.toJson())
        put("charakterkarte", charakterkarte)
        put("rolle", rolle)
    }

    companion object {
        fun fromJson(json: JSONObject): Npc? {
            val id = json.optString("id")
            if (id.isBlank()) return null
            return Npc(
                id = id,
                name = json.optString("name", "Fremde"),
                roomId = json.optString("roomId"),
                tx = json.optInt("tx", 1),
                ty = json.optInt("ty", 1),
                look = Look.fromJson(json.optJSONObject("look")),
                charakterkarte = json.optString("charakterkarte"),
                rolle = json.optString("rolle"),
            )
        }
    }
}

/**
 * Ein manifestiertes Crafting-Rezept: 2–3 sortierte Zutaten-IDs → Ergebnis.
 * [outputItemId] null bedeutet „unmöglich" — auch das ist ein fixer Fakt,
 * der ohne weiteren KI-Aufruf wiederholt wird.
 */
class Recipe(
    val id: String,
    val inputs: List<String>,
    val outputItemId: String?,
    val text: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("inputs", JSONArray().also { arr -> inputs.forEach { arr.put(it) } })
        put("output", outputItemId ?: JSONObject.NULL)
        put("text", text)
    }

    companion object {
        /** Kanonischer Schlüssel einer Kombination (sortierte Item-IDs). */
        fun key(inputs: Collection<String>): String = inputs.sorted().joinToString("+")

        fun fromJson(json: JSONObject): Recipe? {
            val id = json.optString("id")
            if (id.isBlank()) return null
            val inputs = mutableListOf<String>()
            json.optJSONArray("inputs")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optString(i).takeIf { it.isNotBlank() }?.let(inputs::add)
                }
            }
            if (inputs.size < 2) return null
            return Recipe(
                id = id,
                inputs = inputs.sorted(),
                outputItemId = json.optString("output").takeIf { it.isNotBlank() && it != "null" },
                text = json.optString("text"),
            )
        }
    }
}

/** Eine erlangte Fähigkeit. */
class Skill(
    val id: String,
    var name: String,
    var beschreibung: String = "",
    val icon: IconSpec = IconSpec(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("beschreibung", beschreibung)
        put("icon", icon.toJson())
    }

    companion object {
        fun fromJson(json: JSONObject): Skill? {
            val id = json.optString("id")
            if (id.isBlank()) return null
            return Skill(
                id = id,
                name = json.optString("name", "Fähigkeit"),
                beschreibung = json.optString("beschreibung"),
                icon = IconSpec.fromJson(json.optJSONObject("icon")),
            )
        }
    }
}

/** Eine Note einer Melodie: MIDI-Tonhöhe (0 = Pause) und Dauer in Schlägen. */
class Note(val midi: Int, val dauer: Int) {
    fun toJson(): JSONArray = JSONArray().put(midi).put(dauer)

    companion object {
        fun fromJson(arr: JSONArray?): Note? {
            if (arr == null || arr.length() < 2) return null
            return Note(arr.optInt(0, 0), arr.optInt(1, 1).coerceIn(1, 4))
        }
    }
}

/**
 * Eine „MIDI-artige" Melodie als reine Daten — komponierbar von der KI,
 * gerendert vom MelodySynth zu PCM. Jeder Raum kann eine eigene haben.
 */
class MelodySpec(
    val id: String,
    var tempoBpm: Int = 84,
    var timbre: Timbre = Timbre.WEICH,
    val chords: List<Chord> = listOf(Chord.C, Chord.AM, Chord.F, Chord.G),
    val bass: List<Int> = emptyList(),
    val melody: List<Note> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("tempoBpm", tempoBpm)
        put("timbre", timbre.name)
        put("chords", JSONArray().also { arr -> chords.forEach { arr.put(it.name) } })
        put("bass", JSONArray().also { arr -> bass.forEach { arr.put(it) } })
        put("melody", JSONArray().also { arr -> melody.forEach { arr.put(it.toJson()) } })
    }

    companion object {
        fun fromJson(json: JSONObject): MelodySpec? {
            val id = json.optString("id")
            if (id.isBlank()) return null
            val chords = mutableListOf<Chord>()
            json.optJSONArray("chords")?.let { arr ->
                for (i in 0 until arr.length()) {
                    enumOrNull<Chord>(arr.optString(i))?.let(chords::add)
                }
            }
            val bass = mutableListOf<Int>()
            json.optJSONArray("bass")?.let { arr ->
                for (i in 0 until arr.length()) bass.add(arr.optInt(i, 0))
            }
            val melody = mutableListOf<Note>()
            json.optJSONArray("melody")?.let { arr ->
                for (i in 0 until arr.length()) {
                    Note.fromJson(arr.optJSONArray(i))?.let(melody::add)
                }
            }
            return MelodySpec(
                id = id,
                tempoBpm = json.optInt("tempoBpm", 84).coerceIn(60, 120),
                timbre = enumOrNull<Timbre>(json.optString("timbre")) ?: Timbre.WEICH,
                chords = if (chords.isEmpty()) listOf(Chord.C, Chord.AM, Chord.F, Chord.G) else chords,
                bass = bass,
                melody = melody,
            )
        }
    }
}

/**
 * Das geheime Ziel eines Durchlaufs — maschinell prüfbar, damit die Engine
 * das Ende deterministisch erkennt (kein KI-Urteil nötig).
 */
class SecretGoal(
    val typ: GoalType,
    val zielId: String,
    val zustandKey: String? = null,
    val umschreibung: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("typ", typ.name)
        put("zielId", zielId)
        put("zustandKey", zustandKey ?: JSONObject.NULL)
        put("umschreibung", umschreibung)
    }

    companion object {
        fun fromJson(json: JSONObject?): SecretGoal? {
            if (json == null) return null
            val typ = enumOrNull<GoalType>(json.optString("typ")) ?: return null
            val zielId = json.optString("zielId")
            if (zielId.isBlank()) return null
            return SecretGoal(
                typ = typ,
                zielId = zielId,
                zustandKey = json.optString("zustandKey").takeIf { it.isNotBlank() && it != "null" },
                umschreibung = json.optString("umschreibung"),
            )
        }
    }
}

/** Die einmal synthetisierte Story eines Durchlaufs — fix für die Session. */
class Story(
    var titel: String,
    var praemisse: String,
    var ton: String = "",
    val geheimesZiel: SecretGoal,
    val basisStory: String = "",
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("titel", titel)
        put("praemisse", praemisse)
        put("ton", ton)
        put("geheimesZiel", geheimesZiel.toJson())
        put("basisStory", basisStory)
    }

    companion object {
        fun fromJson(json: JSONObject?): Story? {
            if (json == null) return null
            val ziel = SecretGoal.fromJson(json.optJSONObject("geheimesZiel")) ?: return null
            return Story(
                titel = json.optString("titel", "Ohne Titel"),
                praemisse = json.optString("praemisse"),
                ton = json.optString("ton"),
                geheimesZiel = ziel,
                basisStory = json.optString("basisStory"),
            )
        }
    }
}
