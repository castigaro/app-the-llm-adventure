package com.appsonar.thellmadventure.gen

import com.appsonar.thellmadventure.data.GameSave
import com.appsonar.thellmadventure.game.Effect
import com.appsonar.thellmadventure.world.Item
import com.appsonar.thellmadventure.world.Npc
import com.appsonar.thellmadventure.world.PROP_STATES
import com.appsonar.thellmadventure.world.Prop
import com.appsonar.thellmadventure.world.Wall

/**
 * Baut situativ aus dem Weltzustand die Liste der erlaubten Effekt-IDs und
 * löst KI-gemeldete IDs zu echten [Effect]s auf — das Herzstück des
 * Sicherheitsmodells (Whitelist-Muster aus Kap-Krakenzahn). Die KI darf nur
 * anstoßen, was hier vorab als gültig aufgeführt ist; alles andere wird
 * verworfen. So kann kein Prompt das Spiel kaputt machen.
 *
 * Effekt-IDs (Textform für den Prompt und die Antwort):
 *   gib:iX                 — Figur übergibt Gegenstand iX
 *   zustand:pX.key=an/aus  — Requisite pX bekommt Zustand key
 *   oeffne:rX.WAND.pos     — verschlossene Tür entriegeln
 *   nimm:iX                — Gegenstand iX aus Requisite ins Inventar
 *   verbrauche:iX          — Gegenstand iX aus dem Inventar entfernen
 *   erhalte:iX             — versteckten/NPC-Gegenstand iX ins Inventar
 *   fakt:<text>            — freier Fakt
 *   ende                   — geheimes Ziel erreicht (nur wenn tatsächlich erfüllt)
 */
object WhitelistCatalog {

    /** Erlaubte Effekt-IDs für ein Gespräch mit [npc]. */
    fun forDialog(save: GameSave, npc: Npc): List<String> {
        val ids = mutableListOf<String>()
        val world = save.world
        // Gegenstände, die diese Figur besitzt
        world.items.values.filter { it.holder == Item.holderNpc(npc.id) }
            .forEach { ids.add("gib:${it.id}") }
        // Türen im Raum der Figur entriegeln (Figur „kennt den Code").
        // Draußen (npc.roomId = Parzellen-Id) gibt es keine Türen.
        world.room(npc.roomId)?.doors?.filter { it.locked }?.forEach { door ->
            ids.add("oeffne:${npc.roomId}.${door.wall.name}.${door.pos}")
        }
        // Requisiten-Zustände am Ort der Figur (Raum oder Parzelle)
        world.ortProps(npc.roomId).forEach { prop ->
            PROP_STATES.forEach { key -> ids.add("zustand:${prop.id}.$key=an"); ids.add("zustand:${prop.id}.$key=aus") }
        }
        ids.add("fakt")
        return ids
    }

    /** Erlaubte Effekt-IDs für eine Interaktion mit [prop] am Ort [ortId]. */
    fun forMutation(save: GameSave, ortId: String, prop: Prop, withItem: Item?): List<String> {
        val ids = mutableListOf<String>()
        // Zustände genau DIESER Requisite
        PROP_STATES.forEach { key -> ids.add("zustand:${prop.id}.$key=an"); ids.add("zustand:${prop.id}.$key=aus") }
        // In dieser Requisite liegender Gegenstand aufnehmbar
        prop.itemId?.let { ids.add("nimm:$it") }
        // Der benutzte Gegenstand kann verbraucht werden
        withItem?.let { ids.add("verbrauche:${it.id}") }
        // Türen dieses Raums entriegeln (draußen gibt es keine)
        save.world.room(ortId)?.doors?.filter { it.locked }?.forEach { door ->
            ids.add("oeffne:$ortId.${door.wall.name}.${door.pos}")
        }
        ids.add("fakt")
        return ids
    }

    /**
     * Löst eine Liste KI-gemeldeter Effekt-IDs gegen die [erlaubt]-Whitelist
     * auf. Nicht gelistete oder unlesbare IDs werden verworfen. [neuesItem]
     * ist, falls vorhanden, ein separat validierter Gegenstand für `erhalte`.
     */
    fun resolve(
        save: GameSave,
        gemeldet: List<String>,
        erlaubt: Set<String>,
        neuesItemId: String? = null,
    ): List<Effect> {
        val world = save.world
        val effects = mutableListOf<Effect>()
        gemeldet.forEach { raw ->
            val id = raw.trim()
            when {
                id.startsWith("fakt:") -> effects.add(Effect.AddFact(id.removePrefix("fakt:").trim().take(140)))
                id == "fakt" -> {} // ohne Inhalt ignorieren
                id !in erlaubt && !id.startsWith("erhalte:") -> {} // nicht gewhitelistet → verwerfen
                id.startsWith("gib:") -> world.items[id.removePrefix("gib:")]?.let {
                    effects.add(Effect.GiveItemToPlayer(it.id))
                }
                id.startsWith("nimm:") -> world.items[id.removePrefix("nimm:")]?.let {
                    effects.add(Effect.TakeItem(it.id))
                }
                id.startsWith("verbrauche:") -> world.items[id.removePrefix("verbrauche:")]?.let {
                    effects.add(Effect.ConsumeItem(it.id))
                }
                id.startsWith("erhalte:") -> {
                    // Nur der separat validierte neue Gegenstand ist erlaubt.
                    val itemId = id.removePrefix("erhalte:")
                    if (itemId == neuesItemId) world.items[itemId]?.let {
                        effects.add(Effect.GiveItemToPlayer(it.id))
                    }
                }
                id.startsWith("zustand:") -> parseState(id)?.let { (propId, key, value) ->
                    effects.add(Effect.SetPropState(propId, key, value))
                }
                id.startsWith("oeffne:") -> parseDoor(id)?.let { (roomId, wall, pos) ->
                    effects.add(Effect.UnlockDoor(roomId, wall, pos))
                }
            }
        }
        return effects
    }

    private fun parseState(id: String): Triple<String, String, Boolean>? {
        // zustand:pX.key=an  |  zustand:pX.key=aus
        val body = id.removePrefix("zustand:")
        val eq = body.indexOf('=')
        if (eq < 0) return null
        val left = body.substring(0, eq)
        val value = body.substring(eq + 1) == "an"
        val dot = left.indexOf('.')
        if (dot < 0) return null
        val propId = left.substring(0, dot)
        val key = left.substring(dot + 1)
        if (key !in PROP_STATES) return null
        return Triple(propId, key, value)
    }

    private fun parseDoor(id: String): Triple<String, Wall, Int>? {
        // oeffne:rX.WAND.pos
        val parts = id.removePrefix("oeffne:").split('.')
        if (parts.size != 3) return null
        val wall = runCatching { Wall.valueOf(parts[1].uppercase()) }.getOrNull() ?: return null
        val pos = parts[2].toIntOrNull() ?: return null
        return Triple(parts[0], wall, pos)
    }
}
