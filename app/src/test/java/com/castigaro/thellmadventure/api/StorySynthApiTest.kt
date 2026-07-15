package com.castigaro.thellmadventure.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.castigaro.common.llm.ProviderSettings
import com.castigaro.thellmadventure.world.Frame
import com.castigaro.thellmadventure.world.GoalType
import com.castigaro.thellmadventure.world.Item
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
class StorySynthApiTest {

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

    private fun validStory(): String = JSONObject().apply {
        put("titel", "Das Echo der Station")
        put("praemisse", "Juna erwacht allein.")
        put("ton", "still")
        put("zielItem", JSONObject().apply {
            put("name", "Kernschlüssel")
            put("beschreibung", "Warm und schwer.")
            put("icon", JSONObject().put("motiv", "schluessel").put("material", "messing").put("farbe", 3).put("leuchtet", true).put("abgenutzt", false))
        })
        put("zielUmschreibung", "Er öffnet den Reaktor.")
        put("spielerAussehen", JSONObject().put("koerper", "schlank").put("farbeA", 1).put("farbeB", 5).put("kopf", "helm"))
        put("startInventar", JSONArray().put(JSONObject().apply {
            put("name", "Brecheisen")
            put("beschreibung", "Ehrlich.")
            put("icon", JSONObject().put("motiv", "werkzeug").put("material", "eisen").put("farbe", 7).put("leuchtet", false).put("abgenutzt", false))
        }))
        put("startgebiet", JSONObject().apply {
            put("name", "Mondsenke")
            put("palette", "eis")
            put("gelaende", JSONArray().apply {
                repeat(12) { put("gggggggggggg") }
            })
            put("gebaeude", JSONArray().put(JSONObject().apply {
                put("name", "Basisstation"); put("tx", 4); put("ty", 4)
                put("breite", 3); put("tiefe", 2); put("stil", "haus"); put("farbe", 7)
                put("verschlossen", false); put("hinweis", JSONObject.NULL)
            }))
            put("requisiten", JSONArray().put(JSONObject().put("typ", "terminal").put("tx", 2).put("ty", 1).put("name", "Terminal").put("farbe", 7).put("blockiert", true)))
            put("figuren", JSONArray())
        })
    }.toString()

    @Test
    fun `Synthese baut Story mit geheimem Ziel Startgebiet und Inventar`() {
        server.enqueue(anthropic(validStory()))

        val save = StorySynthApi.synthesize(context, Frame.baseStory("mondstation"), listOf("praktisch", "still"))

        assertEquals("Das Echo der Station", save.name)
        assertEquals("Das Echo der Station", save.world.story?.titel)
        assertEquals(GoalType.BESITZE_ITEM, save.world.story?.geheimesZiel?.typ)
        // Spieler startet draußen auf der Startparzelle
        assertEquals(com.castigaro.thellmadventure.data.GameSave.ORT_DRAUSSEN, save.roomId)
        val outdoor = save.world.outdoor!!
        assertEquals(1, outdoor.parcels.size)
        // Das Gebäude ist gesetzt, sein Inneres noch unentdeckt
        assertEquals(1, outdoor.buildings.size)
        assertTrue(outdoor.buildings.values.all { it.targetRoomId == null })
        // Zielobjekt existiert, ist aber noch nicht platziert (versteckt)
        val ziel = save.world.items[save.world.story!!.geheimesZiel.zielId]!!
        assertEquals(StorySynthApi.HOLDER_HIDDEN, ziel.holder)
        // Startinventar liegt im Inventar
        assertTrue(save.world.inventory().any { it.name == "Brecheisen" })
        // Spieler startet auf einer begehbaren Kachel
        assertTrue(outdoor.walkable(save.ptx, save.pty))
    }

    @Test
    fun `Synthese ohne Gebaeude bekommt eine Notfall-Huette`() {
        val ohneGebaeude = JSONObject(validStory()).apply {
            getJSONObject("startgebiet").put("gebaeude", JSONArray())
        }.toString()
        server.enqueue(anthropic(ohneGebaeude))

        val save = StorySynthApi.synthesize(context, Frame.baseStory("mondstation"), listOf("praktisch"))

        val outdoor = save.world.outdoor!!
        assertEquals(1, outdoor.buildings.size)
        // Die Eingangskachel der Hütte ist begehbar
        val huette = outdoor.buildings.values.first()
        assertTrue(outdoor.walkable(huette.doorTile().first, huette.doorTile().second))
    }

    @Test
    fun `ungueltige erste Antwort fuehrt zu einem Wiederholungsversuch`() {
        server.enqueue(anthropic("kein json hier"))       // 1. Versuch scheitert
        server.enqueue(anthropic(validStory()))            // 2. Versuch klappt

        val save = StorySynthApi.synthesize(context, Frame.baseStory("mondstation"), listOf("praktisch"))

        assertNotNull(save.world.story)
        assertEquals(2, server.requestCount)
    }
}
