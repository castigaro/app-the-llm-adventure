package com.appsonar.thellmadventure

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.appsonar.common.llm.ProviderSettingsController
import com.appsonar.thellmadventure.databinding.ActivityAppSettingsBinding

/**
 * App-Einstellungen: KI-Anbindung (Provider, API-Key, Modell, Kosten) über
 * den ProviderSettingsController aus der Common Lib. BEWUSST getrennt von den
 * Spiel-Einstellungen (Audio/Vollbild) — erreichbar nur über das App-Menü
 * auf dem Hauptbildschirm, nie aus dem laufenden Spiel.
 */
class AppSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityAppSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ProviderSettingsController(this, binding.providerSettings)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
