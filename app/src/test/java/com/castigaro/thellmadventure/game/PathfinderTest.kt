package com.castigaro.thellmadventure.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathfinderTest {

    private val frei = { _: Int, _: Int -> true }

    @Test
    fun `direkter Weg auf freiem Feld`() {
        val path = Pathfinder.findPath(10, 10, frei, 0 to 0, 3 to 0)
        assertEquals(listOf(1 to 0, 2 to 0, 3 to 0), path)
    }

    @Test
    fun `Start gleich Ziel ergibt leeren Pfad`() {
        assertTrue(Pathfinder.findPath(10, 10, frei, 4 to 4, 4 to 4).isEmpty())
    }

    @Test
    fun `Umweg um blockierte Kacheln`() {
        // Senkrechte Mauer bei x=2 mit Lücke bei y=4
        val walkable = { x: Int, y: Int -> x != 2 || y == 4 }
        val path = Pathfinder.findPath(6, 6, walkable, 0 to 0, 4 to 0)
        assertTrue(path.isNotEmpty())
        assertEquals(4 to 0, path.last())
        // Der Pfad darf die Mauer nur durch die Lücke queren
        path.filter { it.first == 2 }.forEach { assertEquals(4, it.second) }
        // Alle Schritte sind 4er-Nachbarn
        var prev = 0 to 0
        path.forEach { step ->
            val dist = Math.abs(step.first - prev.first) + Math.abs(step.second - prev.second)
            assertEquals(1, dist)
            prev = step
        }
    }

    @Test
    fun `kein Pfad wenn Ziel eingemauert ist`() {
        val walkable = { x: Int, y: Int -> !(x == 4 && y == 4) }
        // Ziel selbst blockiert
        assertTrue(Pathfinder.findPath(10, 10, walkable, 0 to 0, 4 to 4).isEmpty())
        // Ziel frei, aber komplett umschlossen
        val ring = setOf(3 to 3, 4 to 3, 5 to 3, 3 to 4, 5 to 4, 3 to 5, 4 to 5, 5 to 5)
        val eingemauert = { x: Int, y: Int -> (x to y) !in ring }
        assertTrue(Pathfinder.findPath(10, 10, eingemauert, 0 to 0, 4 to 4).isEmpty())
    }

    @Test
    fun `findPathAdjacent endet neben dem Ziel`() {
        val blocked = 5 to 5
        val walkable = { x: Int, y: Int -> (x to y) != blocked }
        val path = Pathfinder.findPathAdjacent(10, 10, walkable, 0 to 0, blocked)!!
        assertTrue(path.isNotEmpty())
        val ende = path.last()
        val dist = Math.abs(ende.first - blocked.first) + Math.abs(ende.second - blocked.second)
        assertEquals(1, dist)
    }

    @Test
    fun `findPathAdjacent liefert leeren Pfad wenn schon daneben`() {
        val blocked = 5 to 5
        val walkable = { x: Int, y: Int -> (x to y) != blocked }
        val path = Pathfinder.findPathAdjacent(10, 10, walkable, 5 to 4, blocked)
        assertEquals(emptyList<Pair<Int, Int>>(), path)
    }

    @Test
    fun `findPathAdjacent liefert null wenn Ziel komplett umschlossen`() {
        val ring = setOf(3 to 3, 4 to 3, 5 to 3, 3 to 4, 4 to 4, 5 to 4, 3 to 5, 4 to 5, 5 to 5)
        val walkable = { x: Int, y: Int -> (x to y) !in ring }
        assertNull(Pathfinder.findPathAdjacent(10, 10, walkable, 0 to 0, 4 to 4))
    }
}
