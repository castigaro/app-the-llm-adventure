package com.castigaro.thellmadventure.game

import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.world.GoalType
import com.castigaro.thellmadventure.world.Item

/**
 * Prüft nach jeder Zustandsänderung, ob das geheime Ziel erreicht ist —
 * maschinell und deterministisch, ganz ohne KI-Urteil. Wird von der
 * Activity nach jedem [GameEngine.apply] aufgerufen.
 */
object GoalChecker {

    /** true, wenn das geheime Ziel jetzt erfüllt ist (und das Spiel noch läuft). */
    fun isReached(save: GameSave): Boolean {
        if (save.finished) return false
        val goal = save.world.story?.geheimesZiel ?: return false
        return when (goal.typ) {
            GoalType.BESITZE_ITEM ->
                save.world.items[goal.zielId]?.holder == Item.HOLDER_INVENTORY
            GoalType.ERREICHE_RAUM ->
                save.roomId == goal.zielId
            GoalType.ZUSTAND_GESETZT -> {
                val key = goal.zustandKey ?: return false
                save.world.rooms.values
                    .asSequence().flatMap { it.props.asSequence() }
                    .firstOrNull { it.id == goal.zielId }
                    ?.states?.get(key) == true
            }
        }
    }
}
