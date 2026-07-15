package com.castigaro.thellmadventure

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.castigaro.thellmadventure.api.LlmClient
import com.castigaro.thellmadventure.api.StorySynthApi
import com.castigaro.thellmadventure.audio.Speaker
import com.castigaro.thellmadventure.data.SaveStore
import com.castigaro.thellmadventure.data.StoryTemplates
import com.castigaro.thellmadventure.databinding.ActivityNewGameBinding
import com.castigaro.thellmadventure.world.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Der Weg in eine neue Welt: Basis-Story wählen → ein paar verdeckte Fragen
 * (als In-Fiction-Momente getarnt) → die KI webt daraus eine Geschichte mit
 * geheimem Ziel. Die Fragen konfigurieren die Story, ohne dass es wie
 * Konfiguration aussieht.
 */
class NewGameActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNewGameBinding
    private lateinit var baseStory: Frame.BaseStory
    private lateinit var speaker: Speaker
    private val tags = mutableListOf<String>()
    private var questionIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNewGameBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        speaker = Speaker(this)

        showStoryPicker()
    }

    override fun onDestroy() {
        super.onDestroy()
        speaker.shutdown()
    }

    // ---- Schritt 1: Basis-Story wählen ---------------------------------------

    private fun showStoryPicker() {
        binding.questionText.text = getString(R.string.newgame_pick_title)
        binding.optionContainer.removeAllViews()
        Frame.BASIS_STORYS.forEach { story ->
            addOption("${story.titel} — ${story.untertitel}") {
                baseStory = story
                questionIndex = 0
                tags.clear()
                showNextQuestion()
            }
        }
        // Eigene, zuvor gesicherte Geschichten als Startvorlagen anbieten.
        // Vorlagen ohne Außenkarte (altes Format) sind nicht mehr startbar.
        val templates = StoryTemplates.getAll(this).filter { template ->
            template.snapshot.optJSONObject("world")?.optJSONObject("outdoor") != null
        }
        if (templates.isNotEmpty()) {
            addSectionLabel(getString(R.string.newgame_your_stories))
            templates.forEach { template ->
                addOption(getString(R.string.newgame_template_option, template.name)) {
                    startFromTemplate(template)
                }
            }
        }
    }

    private fun startFromTemplate(template: StoryTemplates.Template) {
        val save = StoryTemplates.newSaveFrom(template, template.name)
        SaveStore.add(this, save)
        startActivity(
            android.content.Intent(this, GameActivity::class.java)
                .putExtra(GameActivity.EXTRA_SAVE_ID, save.id)
        )
        finish()
    }

    private fun addSectionLabel(text: String) {
        val label = android.widget.TextView(this).apply {
            this.text = text
            setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Subtitle2)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.option_gap) * 2 }
        }
        binding.optionContainer.addView(label)
    }

    // ---- Schritt 2: verdeckte Intake-Fragen -----------------------------------

    private fun showNextQuestion() {
        if (questionIndex >= baseStory.intake.size) {
            synthesize()
            return
        }
        val question = baseStory.intake[questionIndex]
        binding.questionText.text = question.text
        speaker.speak(this, question.text)
        binding.optionContainer.removeAllViews()
        question.options.forEach { option ->
            addOption(option.label) {
                tags.add(option.tag)
                questionIndex++
                showNextQuestion()
            }
        }
    }

    private fun addOption(label: String, onClick: () -> Unit) {
        val button = com.google.android.material.button.MaterialButton(
            this, null,
            com.google.android.material.R.attr.materialButtonOutlinedStyle,
        ).apply {
            text = label
            isAllCaps = false
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = resources.getDimensionPixelSize(R.dimen.option_gap) }
            setOnClickListener { onClick() }
        }
        binding.optionContainer.addView(button)
    }

    // ---- Schritt 3: KI-Synthese (mit Lade-Overlay) ----------------------------

    private fun synthesize() {
        binding.questionText.text = getString(R.string.newgame_weaving)
        speaker.speak(this, getString(R.string.newgame_weaving))
        binding.optionContainer.removeAllViews()
        setLoading(true, getString(R.string.newgame_weaving_flavor))

        lifecycleScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    StorySynthApi.synthesize(this@NewGameActivity, baseStory, tags.toList())
                }
            }
            setLoading(false, "")
            result.onSuccess { save ->
                SaveStore.add(this@NewGameActivity, save)
                startActivity(
                    android.content.Intent(this@NewGameActivity, GameActivity::class.java)
                        .putExtra(GameActivity.EXTRA_SAVE_ID, save.id)
                )
                finish()
            }.onFailure { error ->
                // Grund ins Logcat — Ursache in der Meldung selbst, weil MIUI
                // den Stacktrace-Anteil von Log.e gelegentlich verschluckt.
                android.util.Log.e(
                    "LlmAdventure",
                    "Story-Synthese fehlgeschlagen: $error / Ursache: ${error.cause}",
                )
                val message = (error as? LlmClient.ApiException)?.message
                    ?: getString(R.string.newgame_weave_failed)
                showRetry(message)
            }
        }
    }

    private fun showRetry(message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.newgame_weave_failed_title)
            .setMessage(message)
            .setPositiveButton(R.string.newgame_retry) { _, _ -> synthesize() }
            .setNegativeButton(R.string.cancel) { _, _ -> showStoryPicker() }
            .setCancelable(false)
            .show()
    }

    private fun setLoading(loading: Boolean, text: String) {
        binding.loadingOverlay.root.visibility = if (loading) View.VISIBLE else View.GONE
        binding.loadingOverlay.loadingText.text = text
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
