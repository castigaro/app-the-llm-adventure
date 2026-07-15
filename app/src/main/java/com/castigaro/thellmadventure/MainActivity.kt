package com.castigaro.thellmadventure

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.castigaro.common.llm.ProviderSettings
import com.castigaro.common.update.UpdateChecker
import com.castigaro.common.update.UpdateUi
import com.castigaro.thellmadventure.data.GameSave
import com.castigaro.thellmadventure.data.SaveStore
import com.castigaro.thellmadventure.databinding.ActivityMainBinding

/**
 * Der Hauptbildschirm: Titel, Spielstandsliste, „Weiterspielen" und „Neues
 * Spiel". Trägt das App-Menü (App-Einstellungen mit Provider/API-Key,
 * Update-Prüfung, Hilfe) — strikt getrennt vom In-Game-Menü.
 *
 * Key-Gate: Ohne hinterlegten API-Key führt jeder Spielstart zum
 * Einrichtungsbildschirm — dieses Spiel lebt von der KI-Generierung.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adapter: SaveAdapter
    private val updateChecker = UpdateChecker(VERSION_URL, BuildConfig.VERSION_CODE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        adapter = SaveAdapter(onTap = ::openSave, onLongPress = ::confirmDelete)
        binding.saveList.layoutManager = LinearLayoutManager(this)
        binding.saveList.adapter = adapter

        binding.buttonContinue.setOnClickListener {
            // „Weiterspielen" nimmt den neuesten LADBAREN Stand (aktuelles Format).
            SaveStore.getAll(this)
                .filter { it.version >= GameSave.VERSION }
                .maxByOrNull { it.updatedAt }
                ?.let(::openSave)
        }
        binding.buttonNewGame.setOnClickListener { startNewGame() }

        UpdateUi.checkSilently(this, binding.root, updateChecker)
    }

    override fun onResume() {
        super.onResume()
        refreshSaves()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_settings -> {
            startActivity(Intent(this, AppSettingsActivity::class.java)); true
        }
        R.id.action_check_update -> {
            UpdateUi.checkManually(this, binding.root, updateChecker); true
        }
        R.id.action_help -> {
            startActivity(Intent(this, HelpActivity::class.java)); true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun refreshSaves() {
        val saves = SaveStore.getAll(this)
        adapter.submit(saves)
        binding.buttonContinue.isEnabled = saves.any { it.version >= GameSave.VERSION }
        binding.noSavesHint.visibility = if (saves.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun startNewGame() {
        if (!ProviderSettings.isConfigured(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            return
        }
        startActivity(Intent(this, NewGameActivity::class.java))
    }

    private fun openSave(save: GameSave) {
        if (save.version < GameSave.VERSION) {
            // Spielstände aus der Zeit vor der Außenkarte sind nicht mehr ladbar.
            AlertDialog.Builder(this)
                .setTitle(R.string.save_old_format_title)
                .setMessage(getString(R.string.save_old_format_message, save.name))
                .setPositiveButton(R.string.delete) { _, _ ->
                    SaveStore.delete(this, save)
                    refreshSaves()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
            return
        }
        startActivity(
            Intent(this, GameActivity::class.java).putExtra(GameActivity.EXTRA_SAVE_ID, save.id)
        )
    }

    private fun confirmDelete(save: GameSave) {
        AlertDialog.Builder(this)
            .setTitle(R.string.main_delete_save_title)
            .setMessage(getString(R.string.main_delete_save_message, save.name))
            .setPositiveButton(R.string.delete) { _, _ ->
                SaveStore.delete(this, save)
                refreshSaves()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    companion object {
        private const val VERSION_URL =
            "https://appsonar.de/downloads/the-llm-adventure-version.json"
    }
}
