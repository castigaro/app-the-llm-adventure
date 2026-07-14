package com.castigaro.thellmadventure.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.castigaro.common.llm.ProviderSettings
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.game.Effect
import com.castigaro.thellmadventure.world.Prop
import com.castigaro.thellmadventure.world.PropType
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.WorldState
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MutationApiTest {

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

    private fun antwort(erfolg: Boolean, vararg effekte: String): String = JSONObject().apply {
        put("text", "Es knirscht.")
        put("erfolg", erfolg)
        put("effekte", JSONArray().apply { effekte.forEach { put(it) } })
    }.toString()

    /** Raum mit einer einzelnen Kiste, ohne Gegenstände oder verschlossene Türen. */
    private fun saveWithProp(): Triple<GameSave, Room, Prop> {
        val world = WorldState()
        val room = Room(world.nextId("r"), "Halle", 6, 4)
        val prop = Prop(world.nextId("p"), PropType.KISTE, 2, 2, "Kiste")
        room.props.add(prop)
        world.addRoom(room)
        return Triple(GameSave(name = "T", world = world, roomId = room.id), room, prop)
    }

    @Test
    fun `nicht gewhitelistete Effekte werden verworfen`() {
        val (save, room, prop) = saveWithProp()
        server.enqueue(anthropic(antwort(
            true,
            "zustand:${prop.id}.offen=an",   // erlaubt (Zustand DIESER Requisite)
            "gib:i99",                        // nicht erlaubt → verwerfen
            "oeffne:${room.id}.N.2",          // keine verschlossene Tür → verwerfen
            "fakt:Die Kiste steht offen.",    // fakt: ist immer erlaubt
        )))

        val res = MutationApi.interact(context, save, room, prop, withItem = null)

        assertTrue(res.erfolg)
        assertEquals(2, res.effekte.size)
        assertTrue(res.effekte.any { it is Effect.SetPropState })
        assertTrue(res.effekte.any { it is Effect.AddFact })
    }

    @Test
    fun `erfolgreiche Interaktion wird gecacht und laeuft danach ohne API`() {
        val (save, room, prop) = saveWithProp()
        server.enqueue(anthropic(antwort(true, "zustand:${prop.id}.offen=an")))

        val first = MutationApi.interact(context, save, room, prop, withItem = null)
        assertFalse(first.gecacht)
        assertEquals(1, server.requestCount)

        val second = MutationApi.interact(context, save, room, prop, withItem = null)
        assertTrue(second.gecacht)
        assertTrue(second.erfolg)
        assertTrue(second.effekte.isEmpty()) // manifestierte Effekte nicht erneut anwenden
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `Misserfolg wird nicht gecacht und erneut beurteilt`() {
        val (save, room, prop) = saveWithProp()
        server.enqueue(anthropic(antwort(false)))
        server.enqueue(anthropic(antwort(false)))

        val first = MutationApi.interact(context, save, room, prop, withItem = null)
        assertFalse(first.erfolg)
        assertTrue(save.world.mutationCache.isEmpty())

        MutationApi.interact(context, save, room, prop, withItem = null)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `API-Fehler wird nicht gecacht`() {
        val (save, room, prop) = saveWithProp()
        // 404 = nicht-transient, wird also nicht automatisch wiederholt.
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"error":{"message":"kaputt"}}"""))

        val result = runCatching { MutationApi.interact(context, save, room, prop, withItem = null) }

        assertTrue(result.isFailure)
        assertTrue(save.world.mutationCache.isEmpty())

        // Nach dem Fehler wird die Interaktion regulär erneut beurteilt
        server.enqueue(anthropic(antwort(true)))
        val retry = MutationApi.interact(context, save, room, prop, withItem = null)
        assertFalse(retry.gecacht)
        assertTrue(retry.erfolg)
        assertEquals(2, server.requestCount)
    }
}
