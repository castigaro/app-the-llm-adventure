package com.castigaro.thellmadventure

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.castigaro.thellmadventure.databinding.ActivitySetupBinding

/**
 * Freundlicher Einrichtungsbildschirm, wenn noch kein API-Key hinterlegt ist.
 * Dieses Spiel LEBT von der KI-Generierung — ohne Key gibt es bewusst keine
 * Offline-Inhalte. Von hier aus geht es zu den Key-Portalen und zu den
 * App-Einstellungen.
 */
class SetupActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.buttonAnthropic.setOnClickListener { open("https://console.anthropic.com/settings/keys") }
        binding.buttonOpenai.setOnClickListener { open("https://platform.openai.com/api-keys") }
        binding.buttonOpenSettings.setOnClickListener {
            startActivity(Intent(this, AppSettingsActivity::class.java))
        }
    }

    private fun open(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
