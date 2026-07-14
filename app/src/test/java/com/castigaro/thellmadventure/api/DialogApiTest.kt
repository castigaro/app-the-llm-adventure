package com.castigaro.thellmadventure.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.castigaro.common.llm.ProviderSettings
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.game.Effect
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Npc
import com.castigaro.thellmadventure.world.Room
import com.castigaro.thellmadventure.world.WorldState
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DialogApiTest {

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

    private fun saveWithNpc(rolle: String): Pair<GameSave, Npc> {
        val world = WorldState()
        val room = Room("r1", "Halle", 8, 6)
        world.addRoom(room)
        val npc = Npc("n1", "Jorin", "r1", 4, 3, charakterkarte = "Mürrisch.", rolle = rolle)
        world.addNpc(npc); room.npcIds.add("n1")
        return GameSave(name = "T", world = world, roomId = "r1") to npc
    }

    @Test
    fun `Dialog liefert Rede und Optionen und filtert Effekte`() {
        val (save, npc) = saveWithNpc("Wächter")
        val ring = Item("i1", "Ring", holder = Item.holderNpc("n1"))
        save.world.addItem(ring)

        server.enqueue(anthropic(JSONObject().apply {
            put("sag", "Was willst du?")
            put("optionen", JSONArray().put("Den Ring, bitte.").put("Nichts."))
            put("effekte", JSONArray().put("gib:i1").put("zustand:p99.an=an")) // zweiter ist ungültig
        }.toString()))

        val turn = DialogApi.turn(context, save, npc, gewaehlteOption = null)

        assertEquals("Was willst du?", turn.sag)
        assertEquals(2, turn.optionen.size)
        // Nur der gültige Effekt bleibt
        assertEquals(1, turn.effekte.size)
        assertTrue(turn.effekte.first() is Effect.GiveItemToPlayer)
    }

    @Test
    fun `neues_item ohne erhalte-NEU wird verworfen`() {
        val (save, npc) = saveWithNpc("Händler") // darf erschaffen
        server.enqueue(anthropic(JSONObject().apply {
            put("sag", "Hier, nimm.")
            put("optionen", JSONArray().put("Danke."))
            put("effekte", JSONArray()) // KEIN erhalte:NEU
            put("neues_item", JSONObject().put("name", "Trank")
                .put("icon", JSONObject().put("form", "flasche").put("farbe", 2).put("zeichen", "T")))
        }.toString()))

        val turn = DialogApi.turn(context, save, npc, gewaehlteOption = null)

        // Ohne erhalte:NEU darf kein Gegenstand ins Inventar wandern
        assertTrue(turn.effekte.none { it is Effect.GiveItemToPlayer })
    }

    @Test
    fun `Gespraech beginnt und endet immer mit einer User-Nachricht`() {
        val (save, npc) = saveWithNpc("Wächter")
        // Frühere Unterhaltung: Eröffnung der Figur (assistant) steht am Anfang
        // UND am Ende der Historie — neuere Modelle lehnen das als Prefill ab.
        save.historyFor(npc.id).add(
            com.castigaro.thellmadventure.data.DialogMessage(
                com.castigaro.thellmadventure.data.DialogMessage.ROLE_NPC, "Geh weiter."
            )
        )
        server.enqueue(anthropic(JSONObject().apply {
            put("sag", "Du schon wieder?")
            put("optionen", JSONArray().put("Ja."))
            put("effekte", JSONArray())
        }.toString()))

        DialogApi.turn(context, save, npc, gewaehlteOption = null)

        val body = JSONObject(server.takeRequest().body.readUtf8())
        val messages = body.getJSONArray("messages")
        assertEquals("user", messages.getJSONObject(0).getString("role"))
        assertEquals("user", messages.getJSONObject(messages.length() - 1).getString("role"))
    }

    @Test
    fun `Rolle ohne Geber-Eigenschaft darf nichts erschaffen`() {
        val (save, npc) = saveWithNpc("Gespenst") // keine Geber-Rolle
        server.enqueue(anthropic(JSONObject().apply {
            put("sag", "Buh.")
            put("optionen", JSONArray().put("Hallo."))
            put("effekte", JSONArray().put("erhalte:NEU"))
            put("neues_item", JSONObject().put("name", "Schatz")
                .put("icon", JSONObject().put("form", "stern").put("farbe", 3).put("zeichen", "S")))
        }.toString()))

        val turn = DialogApi.turn(context, save, npc, gewaehlteOption = null)
        assertTrue(turn.effekte.none { it is Effect.GiveItemToPlayer })
    }
}
