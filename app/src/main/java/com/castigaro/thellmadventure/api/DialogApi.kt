package com.castigaro.thellmadventure.api

import android.content.Context
import com.castigaro.thellmadventure.data.DialogMessage
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.game.Effect
import com.castigaro.thellmadventure.game.StateDigest
import com.castigaro.thellmadventure.gen.ContentValidator
import com.castigaro.thellmadventure.gen.WhitelistCatalog
import com.castigaro.thellmadventure.world.Item
import com.castigaro.thellmadventure.world.Limits
import com.castigaro.thellmadventure.world.Npc
import org.json.JSONArray
import org.json.JSONObject

/**
 * NPC-Dialoge im Optionen-Format (direkt aus Kap-Krakenzahns DialogApi
 * abgeleitet): Der Spieler tippt nie freien Text, sondern wählt aus 2–4 von
 * der KI erzeugten Antworten. Die KI darf Effekte nur aus der Whitelist
 * auslösen und optional EINEN neuen Gegenstand erschaffen — beides streng
 * gefiltert. So bleibt das Gespräch lebendig, ohne die Spiellogik zu
 * gefährden.
 */
object DialogApi {

    /** Ergebnis eines Gesprächszugs. */
    class Turn(
        val sag: String,
        val optionen: List<String>,
        val effekte: List<Effect>,
    )

    /**
     * Ein Gesprächszug. [gewaehlteOption] ist null beim Gesprächsstart
     * (die Figur eröffnet), sonst die vom Spieler gewählte Antwort.
     */
    fun turn(context: Context, save: GameSave, npc: Npc, gewaehlteOption: String?): Turn {
        val erlaubt = WhitelistCatalog.forDialog(save, npc)
        val darfErschaffen = darfErschaffen(save, npc)

        val system = buildString {
            appendLine(
                "Du spielst eine Figur in einem deutschsprachigen Adventure und antwortest " +
                    "NUR mit einem JSON-Objekt: {\"sag\": \"…\", \"optionen\": [\"…\"], \"effekte\": [\"…\"]" +
                    (if (darfErschaffen) ", \"neues_item\": {…} (optional)" else "") + "}."
            )
            appendLine("CHARAKTERKARTE ${npc.name} (${npc.rolle}): ${npc.charakterkarte}")
            appendLine()
            appendLine(StateDigest.build(save))
            appendLine()
            appendLine("REGELN:")
            appendLine("- sag: was die Figur sagt (1-3 Sätze, in ihrer Stimme).")
            appendLine("- optionen: 2-4 Antworten AUS SICHT DES SPIELERS, aus denen er wählt.")
            appendLine("- effekte: nur IDs aus dieser Liste (leer, wenn nichts passiert): ${erlaubt.joinToString(", ")}")
            appendLine("  fakt:<text> für einen knappen Merksatz ist immer erlaubt.")
            if (darfErschaffen) {
                appendLine(
                    "- neues_item + effekt \"erhalte:NEU\": Die Figur kann dem Spieler EINEN neuen, " +
                        "zur Rolle passenden Gegenstand geben. Nur wenn es die Szene wirklich verlangt."
                )
            }
            append("Bleib im Rahmen der Story. Erfinde nichts, was der Spielwelt widerspricht.")
        }

        val turns = buildTurns(save, npc, gewaehlteOption)
        val raw = LlmClient.chat(context, system, turns, maxTokens = 1024, schema = Schemas.dialog())
        return try {
            parse(LlmClient.extractJson(raw), save, npc, erlaubt.toSet(), darfErschaffen)
        } catch (e: Exception) {
            if (e is LlmClient.ApiException && !e.message.orEmpty().startsWith("Antwort nicht lesbar")) throw e
            // Modell hat Prosa statt JSON geliefert (imitiert die Klartext-
            // Historie) → einmal mit Fehler-Feedback erneut versuchen.
            val retryTurns = turns +
                LlmClient.Turn(LlmClient.Turn.ROLE_ASSISTANT, raw) +
                LlmClient.Turn(
                    LlmClient.Turn.ROLE_USER,
                    "Deine Antwort war ungültig (${e.message}). Antworte erneut, NUR mit dem JSON-Objekt nach Schema.",
                )
            val raw2 = LlmClient.chat(context, system, retryTurns, maxTokens = 1024, schema = Schemas.dialog())
            parse(LlmClient.extractJson(raw2), save, npc, erlaubt.toSet(), darfErschaffen)
        }
    }

    /**
     * Baut die Gesprächs-Turns. Wichtig: Die Folge muss mit einer USER-
     * Nachricht beginnen UND enden — die Historie startet mit der Eröffnung
     * der Figur (assistant), und beim Wiederansprechen gäbe es sonst auch am
     * Ende nur assistant. Beides lehnen neuere Modelle mit HTTP 400 ab
     * („does not support assistant prefill").
     */
    private fun buildTurns(save: GameSave, npc: Npc, gewaehlteOption: String?): List<LlmClient.Turn> {
        val history = save.historyFor(npc.id)
        val turns = mutableListOf<LlmClient.Turn>()
        turns.add(LlmClient.Turn(LlmClient.Turn.ROLE_USER, "(Der Spieler tritt heran.)"))
        history.forEach { msg ->
            val role = if (msg.role == DialogMessage.ROLE_PLAYER) LlmClient.Turn.ROLE_USER
            else LlmClient.Turn.ROLE_ASSISTANT
            turns.add(LlmClient.Turn(role, msg.text))
        }
        if (gewaehlteOption != null) {
            turns.add(LlmClient.Turn(LlmClient.Turn.ROLE_USER, gewaehlteOption))
        } else if (history.isNotEmpty()) {
            turns.add(
                LlmClient.Turn(
                    LlmClient.Turn.ROLE_USER,
                    "(Der Spieler kommt erneut auf dich zu und setzt das Gespräch fort.)",
                )
            )
        }
        return turns
    }

    private fun parse(
        json: JSONObject,
        save: GameSave,
        npc: Npc,
        erlaubt: Set<String>,
        darfErschaffen: Boolean,
    ): Turn {
        val sag = json.optString("sag").ifBlank { "…" }.take(400)
        val optionen = mutableListOf<String>()
        json.optJSONArray("optionen")?.let { arr ->
            for (i in 0 until minOf(arr.length(), 4)) {
                arr.optString(i).takeIf { it.isNotBlank() }?.let { optionen.add(it.take(120)) }
            }
        }
        if (optionen.isEmpty()) optionen.add("Verstehe.")

        val gemeldet = mutableListOf<String>()
        json.optJSONArray("effekte")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).takeIf { it.isNotBlank() }?.let(gemeldet::add)
        }

        // Content-Kanal: neuer Gegenstand nur, wenn erlaubt UND "erhalte:NEU" gemeldet.
        var neuesItemId: String? = null
        if (darfErschaffen && gemeldet.any { it.trim() == "erhalte:NEU" }) {
            json.optJSONObject("neues_item")?.let { itemJson ->
                ContentValidator.buildItem(itemJson, save.world)?.let { item ->
                    item.holder = Item.holderNpc(npc.id) // wird durch erhalte→GiveItemToPlayer ins Inventar geholt
                    save.world.addItem(item)
                    neuesItemId = item.id
                }
            }
        }
        val normalisiert = gemeldet.map { if (it.trim() == "erhalte:NEU") "erhalte:${neuesItemId ?: "-"}" else it }

        val effekte = WhitelistCatalog.resolve(save, normalisiert, erlaubt, neuesItemId)
        return Turn(sag, optionen, effekte)
    }

    /** Erschaffen erlaubt: Rolle klingt nach „Geber" und Inventar ist nicht voll. */
    private fun darfErschaffen(save: GameSave, npc: Npc): Boolean {
        if (save.world.inventory().size >= Limits.MAX_INVENTAR) return false
        val rolle = npc.rolle.lowercase()
        return GEBER_ROLLEN.any { rolle.contains(it) }
    }

    private val GEBER_ROLLEN = listOf(
        "händler", "haendler", "krämer", "kraemer", "schmied", "mechaniker",
        "botaniker", "koch", "alchemist", "kaufmann", "kauffrau", "quartiermeister",
        "techniker", "sammler", "hüter", "hueter", "wächter", "waechter",
    )
}
