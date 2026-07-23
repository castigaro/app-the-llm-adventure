package com.appsonar.thellmadventure.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.appsonar.common.llm.ProviderSettings
import com.appsonar.thellmadventure.data.GameSave
import com.appsonar.thellmadventure.world.Building
import com.appsonar.thellmadventure.world.Door
import com.appsonar.thellmadventure.world.GoalType
import com.appsonar.thellmadventure.world.Item
import com.appsonar.thellmadventure.world.Limits
import com.appsonar.thellmadventure.world.OutdoorMap
import com.appsonar.thellmadventure.world.Parcel
import com.appsonar.thellmadventure.world.Room
import com.appsonar.thellmadventure.world.SecretGoal
import com.appsonar.thellmadventure.world.Story
import com.appsonar.thellmadventure.world.Wall
import com.appsonar.thellmadventure.world.WorldState
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RoomGenApiTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        LlmClient.resetForTest()
        LlmClient.anthropicUrl = server.url("/v1/messages").toString()
        ProviderSettings.save(
            context,
            primaryProvider = ProviderSettings.PROVIDER_ANTHROPIC,
            anthropicKey = "sk-test", anthropicModel = "claude-sonnet-5", anthropicEnabled = true,
            openaiKey = "", openaiModel = "gpt-4o-mini", openaiEnabled = false,
        )
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun anthropic(text: String) = MockResponse().setBody(JSONObject().apply {
        put("stop_reason", "end_turn")
        put("usage", JSONObject().put("input_tokens", 10).put("output_tokens", 5))
        put("content", JSONArray().put(JSONObject().put("type", "text").put("text", text)))
    }.toString())

    /** Welt mit einem Startraum, dessen Osttür ins Ungewisse führt. */
    private fun saveWithOrigin(): GameSave {
        val world = WorldState()
        val origin = Room(world.nextId("r"), "Halle", 8, 6)
        origin.doors.add(Door(Wall.O, 3))
        world.addRoom(origin)
        return GameSave(name = "T", world = world, roomId = origin.id)
    }

    private fun validAnswer(zielHier: Boolean = false): String = JSONObject().apply {
        put("raum", JSONObject().apply {
            put("name", "Stille Kammer")
            put("breite", 8)
            put("hoehe", 6)
            put("palette", "stahl")
            put("tueren", JSONArray().put(JSONObject().apply {
                put("wand", "n"); put("pos", 4); put("verschlossen", false); put("name", "Tür")
            }))
            put("requisiten", JSONArray())
            put("figuren", JSONArray())
        })
        put("fakt", "Hier riecht es nach altem Papier.")
        put("zielHier", zielHier)
    }.toString()

    /** Verstecktes Zielobjekt samt Story in die Welt einhängen. */
    private fun hiddenGoal(save: GameSave): Item {
        val world = save.world
        val ziel = Item(world.nextId("i"), "Amulett", holder = StorySynthApi.HOLDER_HIDDEN)
        world.addItem(ziel)
        world.story = Story(
            titel = "Das Echo",
            praemisse = "Etwas wartet.",
            geheimesZiel = SecretGoal(GoalType.BESITZE_ITEM, ziel.id, umschreibung = "Das Amulett"),
        )
        return ziel
    }

    @Test
    fun `erzeugter Raum wird manifestiert und die Tuer verdrahtet`() {
        val save = saveWithOrigin()
        val origin = save.world.room(save.roomId)!!
        val door = origin.doors.first()
        server.enqueue(anthropic(validAnswer()))

        val room = RoomGenApi.generate(context, save, origin, door)

        assertEquals(room.id, door.targetRoomId)
        assertNotNull(save.world.room(room.id))
        // Rücktür durch die Westwand zurück zum Herkunftsraum, offen
        val back = room.doors.first { it.wall == Wall.W }
        assertEquals(origin.id, back.targetRoomId)
        assertTrue(!back.locked)
        // Entdeckung ist als Fakt manifestiert
        assertTrue(save.world.facts.any { it.contains("entdeckt hinter") })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `ungueltige Antwort wird einmal mit Fehlertext erneut versucht`() {
        val save = saveWithOrigin()
        val origin = save.world.room(save.roomId)!!
        server.enqueue(anthropic("{}")) // gültiges JSON, aber ohne 'raum'
        server.enqueue(anthropic(validAnswer()))

        val room = RoomGenApi.generate(context, save, origin, origin.doors.first())

        assertNotNull(save.world.room(room.id))
        assertEquals(2, server.requestCount)
        server.takeRequest()
        val retryBody = server.takeRequest().body.readUtf8()
        assertTrue("Retry muss den Fehler nennen", retryBody.contains("ungültig"))
    }

    @Test
    fun `nach zweitem Fehlversuch bricht die Generierung ab`() {
        val save = saveWithOrigin()
        val origin = save.world.room(save.roomId)!!
        server.enqueue(anthropic("{}"))
        server.enqueue(anthropic("{}"))

        val result = runCatching { RoomGenApi.generate(context, save, origin, origin.doors.first()) }

        assertTrue(result.isFailure)
        assertEquals(2, server.requestCount)
        // Nichts wurde halbfertig manifestiert
        assertEquals(1, save.world.rooms.size)
    }

    @Test
    fun `Zielobjekt wird kurz vor dem Raumlimit zwangsplatziert`() {
        val save = saveWithOrigin()
        val world = save.world
        val ziel = hiddenGoal(save)
        // Welt bis kurz vor das Limit füllen → forcePlace greift trotz zielHier=false
        while (world.rooms.size < Limits.MAX_ORTE - 2) {
            world.addRoom(Room(world.nextId("r"), "Füllraum", 6, 4))
        }
        server.enqueue(anthropic(validAnswer()))

        val origin = world.room(save.roomId)!!
        val room = RoomGenApi.generate(context, save, origin, origin.doors.first())

        // Das Ziel liegt jetzt in einer Requisite des neuen Raums
        assertTrue(ziel.holder.startsWith("prop:"))
        assertTrue(room.props.any { it.itemId == ziel.id })
    }

    @Test
    fun `Zielobjekt wird vor der Mindestraumzahl nicht platziert`() {
        val save = saveWithOrigin()
        val ziel = hiddenGoal(save)
        // Nur der Startraum existiert — die KI will das Ziel trotzdem verstecken.
        server.enqueue(anthropic(validAnswer(zielHier = true)))

        val origin = save.world.room(save.roomId)!!
        val room = RoomGenApi.generate(context, save, origin, origin.doors.first())

        // Der Guard ignoriert zielHier: Das Ziel bleibt verborgen.
        assertEquals(StorySynthApi.HOLDER_HIDDEN, ziel.holder)
        assertTrue(room.props.none { it.itemId == ziel.id })
    }

    @Test
    fun `Zielobjekt darf ab der Mindestraumzahl versteckt werden`() {
        val save = saveWithOrigin()
        val world = save.world
        val ziel = hiddenGoal(save)
        // Mit dem neuen Raum erreicht die Welt genau die Mindestortzahl.
        while (world.rooms.size < Limits.ZIEL_MIN_ORTE - 1) {
            world.addRoom(Room(world.nextId("r"), "Füllraum", 6, 4))
        }
        server.enqueue(anthropic(validAnswer(zielHier = true)))

        val origin = world.room(save.roomId)!!
        val room = RoomGenApi.generate(context, save, origin, origin.doors.first())

        assertTrue(ziel.holder.startsWith("prop:"))
        assertTrue(room.props.any { it.itemId == ziel.id })
    }

    @Test
    fun `generateInterior erzwingt die Ruecktuer nach draussen`() {
        val world = WorldState()
        world.outdoor = OutdoorMap()
        val parcel = Parcel(world.nextId("g"), 0, 0, "Wiese")
        world.outdoor!!.addParcel(parcel)
        val gebaeude = Building(world.nextId("b"), "Kate", tx = 4, ty = 4, w = 2, h = 2)
        world.outdoor!!.buildings[gebaeude.id] = gebaeude
        parcel.buildingIds.add(gebaeude.id)
        val save = GameSave(name = "T", world = world, roomId = GameSave.ORT_DRAUSSEN)
        server.enqueue(anthropic(validAnswer()))

        val room = RoomGenApi.generateInterior(context, save, gebaeude)

        // Das Gebäude ist verdrahtet, die Südwand führt nach draußen
        assertEquals(room.id, gebaeude.targetRoomId)
        val back = room.doors.first { it.wall == Wall.S }
        assertEquals("welt:${gebaeude.id}", back.targetRoomId)
        assertTrue(!back.locked)
    }
}
