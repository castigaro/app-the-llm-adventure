package com.castigaro.thellmadventure.data

import android.content.Context

/**
 * Spiel-Einstellungen in SharedPreferences (Datei "settings" — dieselbe, in
 * der das :common-Modul die Provider-/API-Key-Daten hält; die Schlüssel
 * kollidieren nicht). App-Einstellungen (Provider, Update) und
 * Spiel-Einstellungen (Audio, Vollbild) sind bewusst getrennte Bildschirme.
 */
object AppSettings {

    private fun prefs(context: Context) =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun isMusicEnabled(context: Context): Boolean =
        prefs(context).getBoolean("musicEnabled", true)

    fun setMusicEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("musicEnabled", enabled).apply()
    }

    fun musicVolume(context: Context): Float =
        prefs(context).getFloat("musicVolume", 0.10f)

    fun setMusicVolume(context: Context, volume: Float) {
        prefs(context).edit().putFloat("musicVolume", volume.coerceIn(0f, 1f)).apply()
    }

    fun isSfxEnabled(context: Context): Boolean =
        prefs(context).getBoolean("sfxEnabled", true)

    fun setSfxEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("sfxEnabled", enabled).apply()
    }

    /** Standard für den Vollbildmodus des Spielbildschirms. */
    fun isFullscreen(context: Context): Boolean =
        prefs(context).getBoolean("fullscreen", false)

    fun setFullscreen(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean("fullscreen", enabled).apply()
    }
}
