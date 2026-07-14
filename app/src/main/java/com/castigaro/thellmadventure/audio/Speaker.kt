package com.castigaro.thellmadventure.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.castigaro.thellmadventure.data.AppSettings
import java.util.Locale
import kotlin.math.abs

/**
 * Sprachausgabe per Android Text-to-Speech — portiert von Kap Krakenzahn.
 * Da die Figuren hier zur Laufzeit von der KI erschaffen werden, gibt es
 * keine fest hinterlegten Stimmen: Tonhöhe/Tempo werden pro Figur stabil
 * aus ihrer Id abgeleitet ([voiceFor]). Abschaltbar über [AppSettings].
 *
 * [onSpeaking] meldet Sprech-Beginn und -Ende auf dem Main-Thread, damit
 * die Musik währenddessen geduckt werden kann.
 */
class Speaker(context: Context, private val onSpeaking: ((Boolean) -> Unit)? = null) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val main = Handler(Looper.getMainLooper())

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.GERMAN
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = emitSpeaking(true)
                    override fun onDone(utteranceId: String?) = emitSpeaking(false)
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) = emitSpeaking(false)
                    override fun onStop(utteranceId: String?, interrupted: Boolean) =
                        emitSpeaking(false)
                })
                ready = true
            }
        }
    }

    /** Spricht Text; Default ist die neutrale Erzählstimme. */
    fun speak(context: Context, text: String, pitch: Float = 1f, rate: Float = 1f) {
        if (text.isBlank() || !ready || !AppSettings.isTtsEnabled(context)) return
        val t = tts ?: return
        t.setPitch(pitch)
        t.setSpeechRate(rate)
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tla")
    }

    fun stop() {
        tts?.stop()
        emitSpeaking(false)
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        emitSpeaking(false)
    }

    /** Die TTS-Callbacks kommen auf einem Hintergrund-Thread. */
    private fun emitSpeaking(speaking: Boolean) {
        main.post { onSpeaking?.invoke(speaking) }
    }

    companion object {
        /**
         * Stabile Stimme pro Figur: Tonhöhe ~0.75–1.40, Tempo ~0.85–1.15,
         * deterministisch aus der Figuren-Id abgeleitet.
         */
        fun voiceFor(npcId: String): Pair<Float, Float> {
            val h = abs(npcId.hashCode())
            val pitch = 0.75f + (h % 14) * 0.05f
            val rate = 0.85f + ((h / 14) % 7) * 0.05f
            return pitch to rate
        }
    }
}
