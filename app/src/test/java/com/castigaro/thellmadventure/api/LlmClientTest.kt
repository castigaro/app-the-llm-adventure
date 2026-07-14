package com.castigaro.thellmadventure.api

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.castigaro.common.llm.ProviderSettings
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LlmClientTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        LlmClient.resetForTest()
        LlmClient.anthropicUrl = server.url("/v1/messages").toString()
        LlmClient.openAiUrl = server.url("/v1/chat/completions").toString()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private fun useAnthropic(model: String = "claude-sonnet-5") {
        ProviderSettings.save(
            context,
            primaryProvider = ProviderSettings.PROVIDER_ANTHROPIC,
            anthropicKey = "sk-test", anthropicModel = model, anthropicEnabled = true,
            openaiKey = "", openaiModel = "gpt-4o-mini", openaiEnabled = false,
        )
    }

    private fun useOpenAi(model: String = "gpt-4o-mini") {
        ProviderSettings.save(
            context,
            primaryProvider = ProviderSettings.PROVIDER_OPENAI,
            anthropicKey = "", anthropicModel = "claude-sonnet-5", anthropicEnabled = false,
            openaiKey = "sk-test", openaiModel = model, openaiEnabled = true,
        )
    }

    private fun anthropicBody(text: String): String = JSONObject().apply {
        put("stop_reason", "end_turn")
        put("usage", JSONObject().put("input_tokens", 10).put("output_tokens", 5))
        put("content", org.json.JSONArray().put(JSONObject().put("type", "text").put("text", text)))
    }.toString()

    private fun openAiBody(text: String): String = JSONObject().apply {
        put("usage", JSONObject().put("prompt_tokens", 10).put("completion_tokens", 5))
        put("choices", org.json.JSONArray().put(JSONObject().put(
            "message", JSONObject().put("role", "assistant").put("content", text),
        )))
    }.toString()

    @Test
    fun `Leere Antwort wird einmal automatisch wiederholt`() {
        useAnthropic()
        // Erste Antwort ohne Text-Block (z. B. nur Denk-Blöcke), zweite normal.
        server.enqueue(MockResponse().setBody(JSONObject().apply {
            put("stop_reason", "end_turn")
            put("content", org.json.JSONArray())
        }.toString()))
        server.enqueue(MockResponse().setBody(anthropicBody("da bin ich")))

        val antwort = LlmClient.chat(context, "sys", listOf(LlmClient.Turn("user", "hi")))

        assertEquals("da bin ich", antwort)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `Anthropic-Anfrage traegt Key Version und Schema`() {
        useAnthropic()
        server.enqueue(MockResponse().setBody(anthropicBody("hallo")))

        val schema = JSONObject().put("type", "object")
        val antwort = LlmClient.chat(context, "sys", listOf(LlmClient.Turn("user", "hi")), 2048, schema)

        assertEquals("hallo", antwort)
        val request = server.takeRequest()
        assertEquals("sk-test", request.getHeader("x-api-key"))
        assertEquals("2023-06-01", request.getHeader("anthropic-version"))
        val body = JSONObject(request.body.readUtf8())
        assertEquals(2048, body.getInt("max_tokens"))
        assertEquals("claude-sonnet-5", body.getString("model"))
        assertTrue(body.has("output_config"))
    }

    @Test
    fun `OpenAI-Anfrage nutzt Bearer und response_format`() {
        useOpenAi()
        server.enqueue(MockResponse().setBody(openAiBody("moin")))

        val antwort = LlmClient.chat(context, "sys", listOf(LlmClient.Turn("user", "hi")), 1024, JSONObject().put("type", "object"))

        assertEquals("moin", antwort)
        val request = server.takeRequest()
        assertEquals("Bearer sk-test", request.getHeader("Authorization"))
        val body = JSONObject(request.body.readUtf8())
        assertTrue(body.has("response_format"))
        // System-Prompt als erste Nachricht
        assertEquals("system", body.getJSONArray("messages").getJSONObject(0).getString("role"))
    }

    @Test
    fun `bei HTTP 400 mit Schema wird ohne Schema erneut gefragt`() {
        useAnthropic()
        // Erst Ablehnung des Schemas, dann Erfolg ohne Schema
        server.enqueue(MockResponse().setResponseCode(400).setBody(
            JSONObject().put("error", JSONObject().put("message", "output_config nicht unterstützt")).toString()
        ))
        server.enqueue(MockResponse().setBody(anthropicBody("ohne schema")))

        val antwort = LlmClient.chat(context, "sys", listOf(LlmClient.Turn("user", "hi")), 1024, JSONObject().put("type", "object"))

        assertEquals("ohne schema", antwort)
        server.takeRequest() // erster (mit Schema)
        val second = JSONObject(server.takeRequest().body.readUtf8())
        assertTrue("zweiter Versuch ohne Schema", !second.has("output_config"))
    }

    @Test
    fun `Kosten werden nach einer Anthropic-Antwort verbucht`() {
        useAnthropic()
        ProviderSettings.resetCost(context, ProviderSettings.PROVIDER_ANTHROPIC)
        server.enqueue(MockResponse().setBody(anthropicBody("x")))

        LlmClient.chat(context, "sys", listOf(LlmClient.Turn("user", "hi")), 1024)

        assertTrue(ProviderSettings.getCostMicros(context, ProviderSettings.PROVIDER_ANTHROPIC) >= 0)
    }

    @Test
    fun `extractJson toleriert Zaeune und Fliesstext`() {
        val json = LlmClient.extractJson("Klar! ```json\n{\"a\": 1}\n``` fertig")
        assertEquals(1, json.getInt("a"))
    }
}
