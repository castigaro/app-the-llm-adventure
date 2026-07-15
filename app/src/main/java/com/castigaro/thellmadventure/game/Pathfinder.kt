package com.castigaro.thellmadventure.game

import java.util.ArrayDeque

/**
 * Breitensuche auf einem Kachelgitter. Bewusst simpel gehalten: Räume sind
 * maximal 16×12 Kacheln, die Außenkarte maximal ~24 Parzellen à 12×12 —
 * das sind wenige tausend Kacheln, da braucht es kein A*.
 *
 * Für die Außenkarte gibt es die Varianten mit expliziten Grenzen
 * [minX, minY, maxXExkl, maxYExkl] — dort sind Kachelkoordinaten global
 * und können negativ sein. Die (w, h)-Signaturen delegieren mit (0, 0).
 */
object Pathfinder {

    /**
     * Kürzester Pfad von [start] nach [target] (beide exklusive/inklusive:
     * das Ergebnis enthält [target], aber nicht [start]). Leer, wenn kein
     * Pfad existiert oder Start gleich Ziel ist.
     */
    fun findPath(
        w: Int, h: Int,
        walkable: (Int, Int) -> Boolean,
        start: Pair<Int, Int>,
        target: Pair<Int, Int>,
    ): List<Pair<Int, Int>> = findPath(0, 0, w, h, walkable, start, target)

    fun findPath(
        minX: Int, minY: Int, maxXExkl: Int, maxYExkl: Int,
        walkable: (Int, Int) -> Boolean,
        start: Pair<Int, Int>,
        target: Pair<Int, Int>,
    ): List<Pair<Int, Int>> {
        if (start == target) return emptyList()
        if (target.first !in minX until maxXExkl || target.second !in minY until maxYExkl) return emptyList()
        if (!walkable(target.first, target.second)) return emptyList()

        val prev = HashMap<Pair<Int, Int>, Pair<Int, Int>>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        val seen = HashSet<Pair<Int, Int>>()
        queue.add(start)
        seen.add(start)

        while (queue.isNotEmpty()) {
            val cur = queue.poll() ?: break
            if (cur == target) break
            for ((dx, dy) in NEIGHBORS) {
                val next = cur.first + dx to cur.second + dy
                if (next.first !in minX until maxXExkl || next.second !in minY until maxYExkl) continue
                if (next in seen || !walkable(next.first, next.second)) continue
                seen.add(next)
                prev[next] = cur
                queue.add(next)
            }
        }

        if (target !in prev) return emptyList()
        val path = ArrayList<Pair<Int, Int>>()
        var cur = target
        while (cur != start) {
            path.add(cur)
            cur = prev[cur] ?: return emptyList()
        }
        path.reverse()
        return path
    }

    /**
     * Pfad zur nächstgelegenen begehbaren Nachbarkachel von [target] — zum
     * Herantreten an blockierende Requisiten und Figuren. Steht der Spieler
     * bereits daneben, kommt eine leere Liste zurück (kein Weg nötig).
     */
    fun findPathAdjacent(
        w: Int, h: Int,
        walkable: (Int, Int) -> Boolean,
        start: Pair<Int, Int>,
        target: Pair<Int, Int>,
    ): List<Pair<Int, Int>>? = findPathAdjacent(0, 0, w, h, walkable, start, target)

    fun findPathAdjacent(
        minX: Int, minY: Int, maxXExkl: Int, maxYExkl: Int,
        walkable: (Int, Int) -> Boolean,
        start: Pair<Int, Int>,
        target: Pair<Int, Int>,
    ): List<Pair<Int, Int>>? {
        val candidates = NEIGHBORS
            .map { (dx, dy) -> target.first + dx to target.second + dy }
            .filter {
                it.first in minX until maxXExkl && it.second in minY until maxYExkl &&
                    walkable(it.first, it.second)
            }
        if (candidates.isEmpty()) return null
        if (start in candidates) return emptyList()
        return candidates
            .mapNotNull { cand ->
                findPath(minX, minY, maxXExkl, maxYExkl, walkable, start, cand).takeIf { it.isNotEmpty() }
            }
            .minByOrNull { it.size }
    }

    private val NEIGHBORS = listOf(0 to -1, 1 to 0, 0 to 1, -1 to 0)
}
