package com.castigaro.thellmadventure

import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.castigaro.common.llm.ProviderSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows

/**
 * Key-Gate: Dieses Spiel lebt von der KI-Generierung — ohne hinterlegten
 * API-Key muss jeder Spielstart zum Einrichtungsbildschirm führen.
 */
@RunWith(RobolectricTestRunner::class)
class SetupGateTest {

    private fun tapNewGame(): android.content.Intent? {
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        activity.findViewById<View>(R.id.buttonNewGame).performClick()
        return Shadows.shadowOf(activity).nextStartedActivity
    }

    @Test
    fun `ohne konfigurierten Provider fuehrt Neues Spiel zur Einrichtung`() {
        val next = tapNewGame()
        assertEquals(SetupActivity::class.java.name, next?.component?.className)
    }

    @Test
    fun `mit konfiguriertem Provider startet die Story-Auswahl`() {
        ProviderSettings.save(
            ApplicationProvider.getApplicationContext(),
            primaryProvider = ProviderSettings.PROVIDER_ANTHROPIC,
            anthropicKey = "sk-test", anthropicModel = "claude-sonnet-5", anthropicEnabled = true,
            openaiKey = "", openaiModel = "gpt-4o-mini", openaiEnabled = false,
        )
        val next = tapNewGame()
        assertEquals(NewGameActivity::class.java.name, next?.component?.className)
    }
}
