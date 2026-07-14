package com.castigaro.thellmadventure

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.castigaro.thellmadventure.data.AppSettings
import com.castigaro.thellmadventure.databinding.ActivityGameSettingsBinding
import kotlin.math.roundToInt

/**
 * Spiel-Einstellungen: Musik, Soundeffekte, Lautstärke und Vollbild-Standard.
 * BEWUSST getrennt von den App-Einstellungen (Provider/API-Key) — erreichbar
 * nur aus dem In-Game-Menü.
 */
class GameSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityGameSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.switchMusic.isChecked = AppSettings.isMusicEnabled(this)
        binding.switchSfx.isChecked = AppSettings.isSfxEnabled(this)
        binding.switchTts.isChecked = AppSettings.isTtsEnabled(this)
        binding.switchFullscreen.isChecked = AppSettings.isFullscreen(this)

        binding.switchMusic.setOnCheckedChangeListener { _, v -> AppSettings.setMusicEnabled(this, v) }
        binding.switchSfx.setOnCheckedChangeListener { _, v -> AppSettings.setSfxEnabled(this, v) }
        binding.switchTts.setOnCheckedChangeListener { _, v -> AppSettings.setTtsEnabled(this, v) }
        binding.switchFullscreen.setOnCheckedChangeListener { _, v -> AppSettings.setFullscreen(this, v) }

        binding.sliderMusicVolume.value = sliderValue(AppSettings.musicVolume(this))
        binding.sliderMusicVolume.addOnChangeListener { _, value, _ ->
            AppSettings.setMusicVolume(this, value / 100f)
        }
    }

    private fun sliderValue(volume: Float): Float =
        ((volume * 100 / 5).roundToInt() * 5).coerceIn(0, 100).toFloat()

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
