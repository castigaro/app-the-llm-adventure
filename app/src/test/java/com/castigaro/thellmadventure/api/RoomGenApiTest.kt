package com.castigaro.thellmadventure.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.castigaro.common.llm.ProviderSettings
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.world.Door
import com.castigaro.thellmadventure.world.GoalType
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Limits
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.SecretGoal
import com.castigaro.thellmadventure.world.Story
import com.castigaro.thellmadventure.world.Wall
import com.castigaro.thellmadventure.world.WorldState
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

    private fun validAnswer(): String = JSONObject().apply {
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
        put("zielHier", false)
    }.toString()

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
        val ziel = Item(world.nextId("i"), "Amulett", holder = StorySynthApi.HOLDER_HIDDEN)
        world.addItem(ziel)
        world.story = Story(
            titel = "Das Echo",
            praemisse = "Etwas wartet.",
            geheimesZiel = SecretGoal(GoalType.BESITZE_ITEM, ziel.id, umschreibung = "Das Amulett"),
        )
        // Welt bis kurz vor das Limit füllen → forcePlace greift trotz zielHier=false
        while (world.rooms.size < Limits.MAX_RAEUME - 2) {
            world.addRoom(Room(world.nextId("r"), "Füllraum", 6, 4))
        }
        server.enqueue(anthropic(validAnswer()))

        val origin = world.room(save.roomId)!!
        val room = RoomGenApi.generate(context, save, origin, origin.doors.first())

        // Das Ziel liegt jetzt in einer Requisite des neuen Raums
        assertTrue(ziel.holder.startsWith("prop:"))
        assertTrue(room.props.any { it.itemId == ziel.id })
    }
}
