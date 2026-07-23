package com.appsonar.thellmadventure

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.appsonar.thellmadventure.databinding.ActivityHelpBinding

/** Kurze, statische Hilfe zum Spiel. */
class HelpActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityHelpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
