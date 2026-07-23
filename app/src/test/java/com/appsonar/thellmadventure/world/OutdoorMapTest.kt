package com.appsonar.thellmadventure.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

// Robolectric wegen org.json (toJson/fromJson-Roundtrip).
@RunWith(RobolectricTestRunner::class)
class OutdoorMapTest {

    private fun mapMitZweiParzellen(): OutdoorMap {
        val map = OutdoorMap()
        map.addParcel(Parcel("g1", 0, 0, "Wiese"))
        map.addParcel(Parcel("g2", -1, 0, "Westhang"))
        return map
    }

    @Test
    fun `globale Kacheln finden ihre Parzelle auch bei negativen Koordinaten`() {
        val map = mapMitZweiParzellen()
        assertEquals("g1", map.parcelForTile(0, 0)?.id)
        assertEquals("g1", map.parcelForTile(11, 11)?.id)
        assertEquals("g2", map.parcelForTile(-1, 5)?.id)
        assertEquals("g2", map.parcelForTile(-12, 0)?.id)
        assertNull(map.parcelForTile(12, 0))
        assertNull(map.parcelForTile(0, -1))
    }

    @Test
    fun `walkable beachtet Gelaende Gebaeude und Blocker-Requisiten`() {
        val map = mapMitZweiParzellen()
        val g1 = map.parcels["g1"]!!
        g1.setTerrainLocal(3, 3, Terrain.WASSER)
        assertFalse("Wasser ist nicht begehbar", map.walkable(3, 3))
        assertTrue(map.walkable(4, 4))

        val haus = Building("b1", "Haus", tx = 5, ty = 5, w = 2, h = 2)
        map.buildings[haus.id] = haus
        assertFalse("Gebäudekorpus blockiert", map.walkable(5, 5))
        assertFalse(map.walkable(6, 6))
        assertTrue("Die Eingangskachel bleibt begehbar", map.walkable(haus.doorTile().first, haus.doorTile().second))

        g1.props.add(Prop("p1", PropType.STEIN, 8, 8, "Fels"))
        assertFalse("Blocker-Requisite blockiert", map.walkable(8, 8))
        g1.props.add(Prop("p2", PropType.TEPPICH, 9, 9, "Matte", blocksTile = false))
        assertTrue("Begehbare Requisite blockiert nicht", map.walkable(9, 9))
    }

    @Test
    fun `frontierAt erkennt nur begehbare Randkacheln vor fehlenden Nachbarn`() {
        val map = mapMitZweiParzellen()
        // Ostrand von g1 (lx=11): dahinter fehlt (1,0)
        assertEquals(1 to 0, map.frontierAt(11, 5))
        // Westrand von g1 (lx=0): dahinter liegt g2 → kein Nebelrand
        assertNull(map.frontierAt(0, 5))
        // Nordrand von g2: dahinter fehlt (-1,-1)
        assertEquals(-1 to -1, map.frontierAt(-6, 0))
        // Randkachel auf Wasser ist kein Tor
        map.parcels["g1"]!!.setTerrainLocal(11, 7, Terrain.WASSER)
        assertNull(map.frontierAt(11, 7))
        // Kachel mitten in der Parzelle ist kein Rand
        assertNull(map.frontierAt(5, 5))
    }

    @Test
    fun `bounds umschliessen alle Parzellen`() {
        val map = mapMitZweiParzellen()
        val b = map.bounds()
        assertEquals(-12, b[0])
        assertEquals(0, b[1])
        assertEquals(12, b[2])
        assertEquals(12, b[3])
    }

    @Test
    fun `JSON-Roundtrip erhaelt Gelaende Gebaeude und Requisiten`() {
        val map = mapMitZweiParzellen()
        val g1 = map.parcels["g1"]!!
        g1.setTerrainLocal(2, 3, Terrain.WEG)
        g1.setTerrainLocal(4, 4, Terrain.WASSER)
        g1.props.add(Prop("p1", PropType.BRUNNEN, 6, 6, "Brunnen", colorSlot = 5))
        g1.npcIds.add("n1")
        val haus = Building(
            "b1", "Mühle", tx = 3, ty = 7, w = 3, h = 2,
            stil = BuildingStyle.SCHEUNE, colorSlot = 2,
            targetRoomId = null, locked = true, lockHint = "Zugesperrt.",
        )
        map.buildings[haus.id] = haus
        g1.buildingIds.add(haus.id)

        val geladen = OutdoorMap.fromJson(map.toJson())!!

        assertEquals(map.parcels.keys, geladen.parcels.keys)
        val g1Neu = geladen.parcels["g1"]!!
        assertTrue(g1Neu.terrain.contentEquals(g1.terrain))
        assertEquals(1, g1Neu.props.size)
        assertEquals(listOf("n1"), g1Neu.npcIds)
        assertEquals(listOf("b1"), g1Neu.buildingIds)
        val hausNeu = geladen.buildings["b1"]!!
        assertEquals(BuildingStyle.SCHEUNE, hausNeu.stil)
        assertTrue(hausNeu.locked)
        assertEquals("Zugesperrt.", hausNeu.lockHint)
        assertNull(hausNeu.targetRoomId)
        assertEquals(haus.doorTile(), hausNeu.doorTile())
    }

    @Test
    fun `kaputtes JSON ergibt eine leere aber nutzbare Karte`() {
        val geladen = OutdoorMap.fromJson(org.json.JSONObject("{\"parcels\": \"quatsch\"}"))
        assertNotNull(geladen)
        assertTrue(geladen!!.parcels.isEmpty())
        assertNull(OutdoorMap.fromJson(null))
    }
}
