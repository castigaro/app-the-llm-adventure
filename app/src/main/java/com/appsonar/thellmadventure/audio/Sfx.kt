package com.appsonar.thellmadventure.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.appsonar.thellmadventure.data.AppSettings
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Kurze Soundeffekte — komplett synthetisiert (keine Audio-Assets, Muster
 * aus Kap-Krakenzahn). Jeder Clip wird einmal als PCM erzeugt und über einen
 * frischen AudioTrack abgespielt. Abschaltbar über [AppSettings].
 */
object Sfx {

    const val PICKUP = "pickup"
    const val DOOR = "tuer"
    const val CRAFT = "craft"
    const val SUCCESS = "erfolg"
    const val ERROR = "fehler"

    private const val SAMPLE_RATE = 22050

    fun play(context: Context, name: String) {
        if (!AppSettings.isSfxEnabled(context)) return
        val pcm = clips[name] ?: return
        runCatching { emit(pcm) }
    }

    private val clips: Map<String, ShortArray> by lazy {
        mapOf(
            PICKUP to arpeggio(listOf(660.0, 880.0, 1180.0), 60),
            DOOR to sweep(180.0, 90.0, 320, 0.4),
            CRAFT to arpeggio(listOf(440.0, 587.0, 740.0, 880.0), 70),
            SUCCESS to arpeggio(listOf(523.0, 659.0, 784.0, 1046.0), 90),
            ERROR to sweep(300.0, 120.0, 200, 0.35),
        )
    }

    private fun sine(freq: Double, ms: Int, gain: Double): ShortArray {
        val n = SAMPLE_RATE * ms / 1000
        val out = ShortArray(n)
        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = envelope(i, n)
            out[i] = (sin(2 * PI * freq * t) * env * gain * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun arpeggio(freqs: List<Double>, msEach: Int): ShortArray {
        val parts = freqs.map { sine(it, msEach, 0.35) }
        val out = ShortArray(parts.sumOf { it.size })
        var pos = 0
        parts.forEach { it.copyInto(out, pos); pos += it.size }
        return out
    }

    private fun sweep(fromHz: Double, toHz: Double, ms: Int, gain: Double): ShortArray {
        val n = SAMPLE_RATE * ms / 1000
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val frac = i.toDouble() / n
            val freq = fromHz + (toHz - fromHz) * frac
            phase += 2 * PI * freq / SAMPLE_RATE
            out[i] = (sin(phase) * envelope(i, n) * gain * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun envelope(i: Int, n: Int): Double {
        val attack = (n * 0.05).toInt().coerceAtLeast(1)
        val a = if (i < attack) i.toDouble() / attack else 1.0
        return a * exp(-3.0 * i / n)
    }

    private fun emit(pcm: ShortArray) {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(pcm, 0, pcm.size)
        track.setNotificationMarkerPosition(pcm.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                runCatching { t?.release() }
            }

            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
        track.play()
    }
}
