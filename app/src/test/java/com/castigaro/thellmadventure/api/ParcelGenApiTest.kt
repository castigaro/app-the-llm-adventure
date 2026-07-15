package com.castigaro.thellmadventure.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.castigaro.common.llm.ProviderSettings
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.world.GoalType
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.OutdoorMap
import com.castigaro.thellmadventure.world.Parcel
import com.castigaro.thellmadventure.world.SecretGoal
import com.castigaro.thellmadventure.world.Story
import com.castigaro.thellmadventure.world.Terrain
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
class ParcelGenApiTest {

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

    /** Welt mit einer Startparzelle bei (0,0); der Spieler steht draußen. */
    private fun saveWithStart(): GameSave {
        val world = WorldState()
        world.outdoor = OutdoorMap()
        world.outdoor!!.addParcel(Parcel(world.nextId("g"), 0, 0, "Startwiese"))
        return GameSave(name = "T", world = world, roomId = GameSave.ORT_DRAUSSEN, ptx = 5, pty = 5)
    }

    private fun validAnswer(zielHier: Boolean = false): String = JSONObject().apply {
        put("parzelle", JSONObject().apply {
            put("name", "Nebliges Moor")
            put("palette", "moos")
            put("gelaende", JSONArray().apply { repeat(12) { put("gggggg~~gggg") } })
            put("gebaeude", JSONArray())
            put("requisiten", JSONArray().put(
                JSONObject().put("typ", "kiste").put("tx", 3).put("ty", 3)
                    .put("name", "Moorkiste").put("farbe", 6).put("blockiert", true)
            ))
            put("figuren", JSONArray())
        })
        put("fakt", "Im Moor blubbert es.")
        put("zielHier", zielHier)
    }.toString()

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
    fun `erzeugte Parzelle wird manifestiert und verdrahtet`() {
        val save = saveWithStart()
        server.enqueue(anthropic(validAnswer()))

        val parcel = ParcelGenApi.generate(context, save, gx = 1, gy = 0)

        assertEquals(parcel, save.world.outdoor!!.parcelAt(1, 0))
        assertEquals("Nebliges Moor", parcel.name)
        assertTrue(parcel.props.any { it.name == "Moorkiste" })
        // Entdeckung ist als Fakt manifestiert
        assertTrue(save.world.facts.any { it.contains("aufgedeckt") })
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `Prompt nennt die Nahtkanten existierender Nachbarn woertlich`() {
        val save = saveWithStart()
        // Markante Ostkante der Startparzelle
        val start = save.world.outdoor!!.parcelAt(0, 0)!!
        for (ly in 0 until 12) start.setTerrainLocal(11, ly, if (ly < 6) Terrain.WEG else Terrain.GRAS)
        server.enqueue(anthropic(validAnswer()))

        ParcelGenApi.generate(context, save, gx = 1, gy = 0)

        val body = server.takeRequest().body.readUtf8()
        assertTrue("Nahtkante muss im Prompt stehen", body.contains("wwwwwwgggggg"))
        assertTrue(body.contains("NAHTKANTEN"))
    }

    @Test
    fun `ungueltige Antwort wird einmal mit Fehlertext erneut versucht`() {
        val save = saveWithStart()
        server.enqueue(anthropic("{}")) // gültiges JSON, aber ohne 'parzelle'
        server.enqueue(anthropic(validAnswer()))

        val parcel = ParcelGenApi.generate(context, save, gx = 0, gy = 1)

        assertNotNull(save.world.outdoor!!.parcelAt(0, 1))
        assertEquals("Nebliges Moor", parcel.name)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `Zielobjekt wird vor der Mindestortzahl nicht platziert`() {
        val save = saveWithStart()
        val ziel = hiddenGoal(save)
        server.enqueue(anthropic(validAnswer(zielHier = true)))

        ParcelGenApi.generate(context, save, gx = 1, gy = 0)

        // Der Guard ignoriert zielHier: Das Ziel bleibt verborgen (erst 2 Orte).
        assertEquals(StorySynthApi.HOLDER_HIDDEN, ziel.holder)
    }

    @Test
    fun `Zielobjekt darf ab der Mindestortzahl versteckt werden`() {
        val save = saveWithStart()
        val ziel = hiddenGoal(save)
        // Karte bis zur Mindestortzahl auffüllen (mit dem Neuen erreicht)
        val outdoor = save.world.outdoor!!
        var gx = 0
        while (save.world.orteCount() < com.castigaro.thellmadventure.world.Limits.ZIEL_MIN_ORTE - 1) {
            gx++
            outdoor.addParcel(Parcel(save.world.nextId("g"), gx, 1, "Füllgebiet"))
        }
        server.enqueue(anthropic(validAnswer(zielHier = true)))

        val parcel = ParcelGenApi.generate(context, save, gx = 1, gy = 0)

        assertTrue(ziel.holder.startsWith("prop:"))
        assertTrue(parcel.props.any { it.itemId == ziel.id })
    }
}
