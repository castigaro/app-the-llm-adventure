package com.appsonar.thellmadventure.gen

import com.appsonar.thellmadventure.game.Pathfinder
import com.appsonar.thellmadventure.world.Limits
import com.appsonar.thellmadventure.world.OutdoorMap
import com.appsonar.thellmadventure.world.Parcel
import com.appsonar.thellmadventure.world.Terrain
import com.appsonar.thellmadventure.world.WorldState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentValidatorParcelTest {

    private fun worldMitKarte(): WorldState {
        val world = WorldState()
        world.outdoor = OutdoorMap()
        return world
    }

    private fun parcelJson(build: JSONObject.() -> Unit = {}): JSONObject =
        JSONObject().apply {
            put("name", "Testgebiet")
            put("palette", "moos")
            put("gelaende", JSONArray().apply { repeat(12) { put("gggggggggggg") } })
            put("gebaeude", JSONArray())
            put("requisiten", JSONArray())
            put("figuren", JSONArray())
            build()
        }

    @Test
    fun `Gelaendezeilen werden gepolstert und unbekannte Zeichen werden Gras`() {
        val world = worldMitKarte()
        val json = parcelJson {
            put("gelaende", JSONArray().apply {
                put("~~X")            // zu kurz, unbekanntes Zeichen
                put("wwwwwwwwwwwwwww") // zu lang
                // die übrigen Zeilen fehlen komplett
            })
        }
        val parcel = ContentValidator.buildParcel(json, world, 0, 0)

        assertEquals(Terrain.WASSER, parcel.terrainLocal(0, 0))
        assertEquals(Terrain.GRAS, parcel.terrainLocal(2, 0))  // 'X' → Gras
        assertEquals(Terrain.GRAS, parcel.terrainLocal(3, 0))  // fehlend → Gras
        assertEquals(Terrain.WEG, parcel.terrainLocal(11, 1))  // gekürzt auf 12
        assertEquals(Terrain.GRAS, parcel.terrainLocal(5, 5))  // fehlende Zeile → Gras
    }

    @Test
    fun `Nahtkante wird an begehbaren Nachbarn angepasst`() {
        val world = worldMitKarte()
        // Bestehender Nachbar westlich: komplett Gras (Ostkante offen)
        world.outdoor!!.addParcel(Parcel("g1", -1, 0, "Wiese"))
        // Neue Parzelle: Westkante komplett Wasser
        val json = parcelJson {
            put("gelaende", JSONArray().apply { repeat(12) { put("~ggggggggggg") } })
        }
        val parcel = ContentValidator.buildParcel(json, world, 0, 0)

        // Mindestens GATE_BREITE zusammenhängende Westkacheln wurden begehbar geschnitten
        val begehbar = (0 until 12).count { parcel.terrainLocal(0, it).begehbar }
        assertTrue("Naht braucht ein Tor, begehbar=$begehbar", begehbar >= 3)
        // Das Tor ist beidseitig nutzbar
        val tor = (0 until 12).first { parcel.terrainLocal(0, it).begehbar }
        assertTrue(world.outdoor!!.walkable(0, tor))
        assertTrue(world.outdoor!!.walkable(-1, tor))
    }

    @Test
    fun `geschlossenes Nachbarufer laesst die Naht zu`() {
        val world = worldMitKarte()
        // Nachbar westlich mit komplett unbegehbarer Ostkante (Felswand)
        val nachbar = Parcel("g1", -1, 0, "Klippen")
        for (ly in 0 until 12) nachbar.setTerrainLocal(11, ly, Terrain.FELS)
        world.outdoor!!.addParcel(nachbar)

        val json = parcelJson {
            put("gelaende", JSONArray().apply { repeat(12) { put("~ggggggggggg") } })
        }
        val parcel = ContentValidator.buildParcel(json, world, 0, 0)

        // Kein Tor erzwungen — die Westkante bleibt Wasser
        assertTrue((0 until 12).none { parcel.terrainLocal(0, it).begehbar })
    }

    @Test
    fun `Wasseranteil wird auf den Mindestanteil begehbaren Gelaendes begrenzt`() {
        val world = worldMitKarte()
        val json = parcelJson {
            put("gelaende", JSONArray().apply { repeat(12) { put("~~~~~~~~~~~~") } })
        }
        val parcel = ContentValidator.buildParcel(json, world, 0, 0)

        val begehbar = (0 until 12 * 12).count {
            Terrain.fromZeichen(parcel.terrain[it])?.begehbar == true
        }
        assertTrue(begehbar >= (144 * Limits.MIN_BEGEHBAR_ANTEIL).toInt())
    }

    @Test
    fun `Gebaeude werden geklemmt und ihre Eingangskachel freigeschnitten`() {
        val world = worldMitKarte()
        val json = parcelJson {
            put("gelaende", JSONArray().apply { repeat(12) { put("~~~~~~~~~~~~") } })
            put("gebaeude", JSONArray()
                .put(JSONObject().apply {
                    put("name", "Mühle"); put("tx", 99); put("ty", -5)
                    put("breite", 9); put("tiefe", 1); put("stil", "scheune"); put("farbe", 2)
                    put("verschlossen", false); put("hinweis", JSONObject.NULL)
                })
            )
        }
        val parcel = ContentValidator.buildParcel(json, world, 0, 0)

        assertEquals(1, parcel.buildingIds.size)
        val haus = world.outdoor!!.buildings[parcel.buildingIds.first()]!!
        // Grundriss geklemmt auf 2..3 und in die Parzelle (mit Rand)
        assertTrue(haus.w in 2..3 && haus.h in 2..3)
        assertTrue(haus.tx >= 1 && haus.tx + haus.w <= 11)
        // Die Eingangskachel wurde begehbar geschnitten
        val tuer = haus.doorTile()
        assertTrue(world.outdoor!!.walkable(tuer.first, tuer.second))
    }

    @Test
    fun `Requisiten nur auf begehbarem freiem Gelaende`() {
        val world = worldMitKarte()
        val json = parcelJson {
            put("gelaende", JSONArray().apply {
                put("~ggggggggggg")
                repeat(11) { put("gggggggggggg") }
            })
            put("requisiten", JSONArray()
                .put(JSONObject().put("typ", "stein").put("tx", 0).put("ty", 0).put("name", "Im Wasser").put("farbe", 0).put("blockiert", true))
                .put(JSONObject().put("typ", "warpkern").put("tx", 3).put("ty", 3).put("name", "Unsinn").put("farbe", 0).put("blockiert", true))
                .put(JSONObject().put("typ", "brunnen").put("tx", 5).put("ty", 5).put("name", "Brunnen").put("farbe", 5).put("blockiert", true))
                .put(JSONObject().put("typ", "fass").put("tx", 5).put("ty", 5).put("name", "Doppelt belegt").put("farbe", 6).put("blockiert", true))
            )
        }
        val parcel = ContentValidator.buildParcel(json, world, 0, 0)

        assertEquals(1, parcel.props.size)
        val brunnen = parcel.props.first()
        assertEquals("Brunnen", brunnen.name)
        // Lokale (5,5) wurde zur globalen Kachel
        assertEquals(5 to 5, brunnen.tx to brunnen.ty)
    }

    @Test
    fun `Figuren landen auf freien Kacheln mit Parzellen-Id als Ort`() {
        val world = worldMitKarte()
        val json = parcelJson {
            put("figuren", JSONArray().put(JSONObject().apply {
                put("name", "Wanderin"); put("rolle", "Botin"); put("charakterkarte", "Eilig.")
                put("aussehen", JSONObject().put("koerper", "schlank").put("farbeA", 1).put("farbeB", 2).put("kopf", "kapuze"))
                put("tx", 4); put("ty", 4)
            }))
        }
        val parcel = ContentValidator.buildParcel(json, world, 0, 0)

        assertEquals(1, parcel.npcIds.size)
        val npc = world.npcs[parcel.npcIds.first()]!!
        assertEquals(parcel.id, npc.roomId)
        assertTrue(world.outdoor!!.walkable(npc.tx, npc.ty))
    }

    @Test
    fun `Zusammenhang wird ueber die ganze Karte erzwungen`() {
        val world = worldMitKarte()
        world.outdoor!!.addParcel(Parcel("g1", -1, 0, "Wiese"))
        // Neue Parzelle: eine Wasserwand trennt die Westkante vom Rest
        val json = parcelJson {
            put("gelaende", JSONArray().apply { repeat(12) { put("g~gggggggggg") } })
        }
        val parcel = ContentValidator.buildParcel(json, world, 0, 0)

        // Vom Nachbartor aus muss die Parzellenmitte erreichbar sein
        val tor = (0 until 12).first { parcel.terrainLocal(0, it).begehbar }
        val b = world.outdoor!!.bounds()
        val pfad = Pathfinder.findPath(
            b[0], b[1], b[2], b[3], world.outdoor!!::walkable,
            0 to tor, 6 to 6,
        )
        assertTrue("Tor muss die Mitte erreichen", pfad.isNotEmpty())
    }

    @Test
    fun `doppelte Parzelle am selben Raster wird abgewiesen`() {
        val world = worldMitKarte()
        ContentValidator.buildParcel(parcelJson(), world, 0, 0)
        val result = runCatching { ContentValidator.buildParcel(parcelJson(), world, 0, 0) }
        assertTrue(result.isFailure)
    }
}
