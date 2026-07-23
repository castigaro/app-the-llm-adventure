package com.appsonar.thellmadventure.game

import com.appsonar.thellmadventure.data.GameSave
import com.appsonar.thellmadventure.world.Item
import com.appsonar.thellmadventure.world.PROP_STATES
import com.appsonar.thellmadventure.world.Wall

/**
 * Alles, was den Weltzustand verändert, läuft als [Effect] durch diese
 * deterministische Engine — auch KI-Antworten dürfen nur gefilterte Effekte
 * auslösen (Whitelist-Muster aus Kap-Krakenzahn). Jede Änderung hinterlässt
 * einen Fakt im Welt-Gedächtnis.
 */
sealed class Effect {

    /** Gegenstand aus einer Requisite ins Inventar — verschwindet aus dem Raum. */
    class TakeItem(val itemId: String) : Effect()

    /** Gegenstand aus dem Inventar entfernen (verbraucht). */
    class ConsumeItem(val itemId: String) : Effect()

    /** Eine Figur übergibt einen Gegenstand an den Spieler. */
    class GiveItemToPlayer(val itemId: String) : Effect()

    /** Zustand einer Requisite setzen (nur Schlüssel aus PROP_STATES). */
    class SetPropState(val propId: String, val key: String, val value: Boolean) : Effect()

    /** Eine verschlossene Tür entriegeln. */
    class UnlockDoor(val roomId: String, val wall: Wall, val pos: Int) : Effect()

    /** Eine Fähigkeit erlangen (muss bereits in world.skills liegen). */
    class TeachSkill(val skillId: String) : Effect()

    /** Freier Fakt ins Welt-Gedächtnis. */
    class AddFact(val text: String) : Effect()

    /** Das geheime Ziel ist erreicht — Spielende. */
    object EndGame : Effect()
}

object GameEngine {

    /**
     * Wendet Effekte in Reihenfolge an. Unbekannte Referenzen werden still
     * übersprungen (defensiv gegen KI-Antwortdrift); alles Angewandte
     * hinterlässt einen Fakt.
     */
    fun apply(save: GameSave, effects: List<Effect>) {
        val world = save.world
        effects.forEach { effect ->
            when (effect) {
                is Effect.TakeItem -> {
                    val item = world.items[effect.itemId] ?: return@forEach
                    val prop = world.propHolding(effect.itemId)
                    if (prop != null) {
                        prop.itemId = null
                        world.addFact("${item.id} aufgenommen aus ${prop.id}")
                    } else {
                        world.addFact("${item.id} aufgenommen")
                    }
                    item.holder = Item.HOLDER_INVENTORY
                }
                is Effect.ConsumeItem -> {
                    val item = world.items[effect.itemId] ?: return@forEach
                    if (item.holder != Item.HOLDER_INVENTORY) return@forEach
                    item.holder = Item.HOLDER_CONSUMED
                    world.addFact("${item.id} verbraucht")
                }
                is Effect.GiveItemToPlayer -> {
                    val item = world.items[effect.itemId] ?: return@forEach
                    val vorher = item.holder
                    item.holder = Item.HOLDER_INVENTORY
                    if (vorher.startsWith("npc:")) {
                        world.addFact("${vorher.removePrefix("npc:")} gab ${item.id} an Spieler")
                    } else {
                        world.addFact("Spieler erhielt ${item.id}")
                    }
                }
                is Effect.SetPropState -> {
                    if (effect.key !in PROP_STATES) return@forEach
                    val prop = world.allProps()
                        .firstOrNull { it.id == effect.propId } ?: return@forEach
                    prop.states[effect.key] = effect.value
                    world.addFact("${prop.id} ${effect.key}=${if (effect.value) "ja" else "nein"}")
                }
                is Effect.UnlockDoor -> {
                    val room = world.room(effect.roomId) ?: return@forEach
                    val door = room.doors.firstOrNull { it.wall == effect.wall && it.pos == effect.pos }
                        ?: return@forEach
                    if (!door.locked) return@forEach
                    door.locked = false
                    world.addFact("Tür ${room.id}.${door.wall.name} entriegelt")
                }
                is Effect.TeachSkill -> {
                    val skill = world.skills[effect.skillId] ?: return@forEach
                    world.addFact("Fähigkeit ${skill.id} (${skill.name}) erlangt")
                }
                is Effect.AddFact -> world.addFact(effect.text)
                is Effect.EndGame -> {
                    save.finished = true
                    world.addFact("Das geheime Ziel wurde erreicht")
                }
            }
        }
    }
}
