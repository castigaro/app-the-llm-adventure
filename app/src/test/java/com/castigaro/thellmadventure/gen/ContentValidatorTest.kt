package com.castigaro.thellmadventure.gen

import com.castigaro.thellmadventure.game.Pathfinder
import com.castigaro.thellmadventure.world.Door
import com.castigaro.thellmadventure.world.IconArchetype
import com.castigaro.thellmadventure.world.IconMaterial
import com.castigaro.thellmadventure.world.Limits
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.SLOT_COLORS
import com.castigaro.thellmadventure.world.Wall
import com.castigaro.thellmadventure.world.WorldState
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentValidatorTest {

    private fun roomJson(build: JSONObject.() -> Unit): JSONObject =
        JSONObject().apply {
            put("name", "Testraum")
            put("breite", 8)
            put("hoehe", 6)
            put("palette", "stahl")
            put("tueren", JSONArray())
            put("requisiten", JSONArray())
            put("figuren", JSONArray())
            build()
        }

    @Test
    fun `zu grosser Raum wird auf das Maximum geklemmt`() {
        val world = WorldState()
        val json = roomJson {
            put("breite", 99)
            put("hoehe", 99)
        }
        val room = ContentValidator.buildRoom(json, world)
        assertEquals(Limits.MAX_RAUM_W, room.w)
        assertEquals(Limits.MAX_RAUM_H, room.h)
    }

    @Test
    fun `unbekannter Requisiten-Typ fliegt raus`() {
        val world = WorldState()
        val json = roomJson {
            put("requisiten", JSONArray().apply {
                put(prop("kiste", 2, 2))
                put(prop("warpkern", 3, 3))
            })
        }
        val room = ContentValidator.buildRoom(json, world)
        assertEquals(1, room.props.size)
        assertEquals("KISTE", room.props.first().type.name)
    }

    @Test
    fun `Requisiten auf gleicher Kachel werden nicht doppelt platziert`() {
        val world = WorldState()
        val json = roomJson {
            put("requisiten", JSONArray().apply {
                put(prop("kiste", 2, 2))
                put(prop("fass", 2, 2))
            })
        }
        val room = ContentValidator.buildRoom(json, world)
        assertEquals(1, room.props.size)
    }

    @Test
    fun `Ruecktuer zum Herkunftsraum wird erzwungen und ist offen`() {
        val world = WorldState()
        val origin = Room("r1", "Halle", 8, 6)
        val door = Door(Wall.O, 3) // Spieler geht durch Ostwand …
        val json = roomJson {
            // KI liefert KEINE Rücktür
            put("tueren", JSONArray().put(tuer("n", 4)))
        }
        val room = ContentValidator.buildRoom(json, world, origin = origin to door)
        // … also muss im neuen Raum eine Westtür zurück nach r1 entstehen
        val back = room.doors.firstOrNull { it.wall == Wall.W }
        assertNotNull(back)
        assertEquals("r1", back!!.targetRoomId)
        assertTrue(!back.locked)
    }

    @Test
    fun `blockierende Requisiten die Tueren abschneiden werden begehbar gemacht`() {
        val world = WorldState()
        // Zwei Türen an gegenüberliegenden Wänden, dazwischen eine volle Mauer
        val json = roomJson {
            put("breite", 5)
            put("hoehe", 5)
            put("tueren", JSONArray().apply {
                put(tuer("n", 2))
                put(tuer("s", 2))
            })
            put("requisiten", JSONArray().apply {
                // Mauer quer über y=2 blockiert den Weg zwischen den Türen
                for (x in 0 until 5) put(prop("stein", x, 2))
            })
        }
        val room = ContentValidator.buildRoom(json, world)
        val nord = room.doors.first { it.wall == Wall.N }.tile(room.w, room.h)
        val sued = room.doors.first { it.wall == Wall.S }.tile(room.w, room.h)
        val pfad = Pathfinder.findPath(room.w, room.h, room::walkable, nord, sued)
        assertTrue("Türen müssen verbunden sein", pfad.isNotEmpty())
    }

    @Test
    fun `enthaltener Gegenstand wird angelegt und verdrahtet`() {
        val world = WorldState()
        val json = roomJson {
            put("requisiten", JSONArray().apply {
                put(prop("truhe", 2, 2).apply {
                    put("enthaeltItem", JSONObject().apply {
                        put("name", "Goldkelch")
                        put("beschreibung", "Glänzt.")
                        put("icon", JSONObject().put("motiv", "krug").put("material", "messing").put("farbe", 3).put("leuchtet", false).put("abgenutzt", false))
                    })
                })
            })
        }
        val room = ContentValidator.buildRoom(json, world)
        val truhe = room.props.first()
        assertNotNull(truhe.itemId)
        val item = world.items[truhe.itemId]
        assertNotNull(item)
        assertEquals("Goldkelch", item!!.name)
        assertEquals("prop:${truhe.id}", item.holder)
    }

    @Test
    fun `unbekanntes Icon-Motiv faellt auf die Namens-Ableitung zurueck`() {
        val icon = ContentValidator.buildIcon(
            JSONObject().put("motiv", "portal").put("farbe", 2), "Schlüssel"
        )
        // Kein Absturz — und der Name führt zum passenden Motiv
        assertEquals(IconArchetype.SCHLUESSEL, icon.motiv)
    }

    @Test
    fun `gueltiges Icon-Motiv wird samt Material und Flaggen uebernommen`() {
        val icon = ContentValidator.buildIcon(
            JSONObject().put("motiv", "kristall").put("material", "kristall")
                .put("farbe", 99).put("leuchtet", true).put("abgenutzt", true),
            "Seelenstein",
        )
        assertEquals(IconArchetype.KRISTALL, icon.motiv)
        assertEquals(IconMaterial.KRISTALL, icon.material)
        assertTrue(icon.leuchtet)
        assertTrue(icon.abgenutzt)
        // Farb-Slot wird in den gültigen Bereich geklemmt
        assertTrue(icon.colorIdx in SLOT_COLORS.indices)
    }

    @Test
    fun `zu viele Tueren werden auf das Maximum gekappt`() {
        val world = WorldState()
        val json = roomJson {
            put("tueren", JSONArray().apply {
                put(tuer("n", 1)); put(tuer("o", 1)); put(tuer("s", 1))
                put(tuer("w", 1)); put(tuer("n", 3)); put(tuer("s", 3))
            })
        }
        val room = ContentValidator.buildRoom(json, world)
        assertTrue(room.doors.size <= Limits.MAX_TUEREN)
    }

    @Test
    fun `Melodie wird in den Rahmen geklemmt`() {
        val world = WorldState()
        val json = JSONObject().apply {
            put("tempo", 999)
            put("klang", "weich")
            put("akkorde", JSONArray().put("c").put("g"))
            put("bass", JSONArray().put(48).put(200))
            put("melodie", JSONArray().apply {
                put(JSONArray().put(200).put(1)) // zu hoch → geklemmt
                put(JSONArray().put(0).put(9))    // Pause, Dauer geklemmt
            })
        }
        val melody = ContentValidator.buildMelody(json, world)!!
        assertTrue(melody.tempoBpm in 60..120)
        assertTrue(melody.melody.all { it.midi == 0 || it.midi in 48..84 })
        assertTrue(melody.melody.all { it.dauer in 1..4 })
        assertTrue(melody.bass.all { it in 36..72 })
    }

    @Test
    fun `Melodie ohne Akkorde ist null`() {
        val world = WorldState()
        assertNull(ContentValidator.buildMelody(JSONObject().put("akkorde", JSONArray()), world))
    }

    private fun prop(typ: String, tx: Int, ty: Int): JSONObject = JSONObject().apply {
        put("typ", typ)
        put("tx", tx)
        put("ty", ty)
        put("name", typ)
        put("farbe", 0)
        put("blockiert", true)
    }

    private fun tuer(wand: String, pos: Int): JSONObject = JSONObject().apply {
        put("wand", wand)
        put("pos", pos)
        put("verschlossen", false)
        put("name", "Tür")
    }
}
