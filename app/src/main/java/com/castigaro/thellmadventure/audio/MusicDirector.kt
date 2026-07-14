package com.castigaro.thellmadventure.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.castigaro.thellmadventure.data.AppSettings
import com.castigaro.thellmadventure.world.MelodySpec

/**
 * Spielt je Raum die passende Hintergrundmusik (Muster aus Kap-Krakenzahns
 * Ambience.switchTo): beim Raumwechsel wird der Loop getauscht. Gerenderte
 * PCM-Loops werden in einem kleinen LRU-Cache gehalten, damit ein
 * wiederbetretener Raum sofort klingt. Schalter und Lautstärke über
 * [AppSettings]; die KI kann eigene Melodien beisteuern.
 */
class MusicDirector(private val context: Context) {

    private var track: AudioTrack? = null
    private var currentId: String? = null
    private var ducked = false

    /** LRU-Cache gerenderter Loops (Spec-Id → PCM). */
    private val cache = object : LinkedHashMap<String, ShortArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, ShortArray>): Boolean = size > 8
    }

    /**
     * Wechselt zur Melodie eines Raums. [spec] ist die manifestierte
     * KI-Melodie (oder null → eingebaute nach Raum-Id).
     */
    fun switchTo(roomId: String, spec: MelodySpec?) {
        if (!AppSettings.isMusicEnabled(context)) {
            stop()
            return
        }
        val melody = spec ?: Melodies.forRoom(roomId)
        if (melody.id == currentId && track != null) {
            applyVolume()
            return
        }
        stop()
        val pcm = cache.getOrPut(melody.id) { MelodySynth.render(melody) }
        runCatching { startLoop(pcm) }
        currentId = melody.id
    }

    fun stop() {
        runCatching {
            track?.pause()
            track?.flush()
            track?.release()
        }
        track = null
        currentId = null
    }

    fun applyVolume() {
        val gain = AppSettings.musicVolume(context) * (if (ducked) DUCK_FACTOR else 1f)
        runCatching { track?.setVolume(gain) }
    }

    /** Musik leiser drehen, solange die Sprachausgabe spricht. */
    fun duck(active: Boolean) {
        ducked = active
        applyVolume()
    }

    private companion object {
        const val DUCK_FACTOR = 0.3f
    }

    private fun startLoop(pcm: ShortArray) {
        if (pcm.isEmpty()) return
        val t = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(MelodySynth.SAMPLE_RATE)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        t.write(pcm, 0, pcm.size)
        t.setLoopPoints(0, pcm.size, -1)
        track = t
        applyVolume()
        t.play()
    }
}
