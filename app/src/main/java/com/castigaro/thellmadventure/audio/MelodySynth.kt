package com.castigaro.thellmadventure.audio

import com.castigaro.thellmadventure.world.MelodySpec
import com.castigaro.thellmadventure.world.Timbre
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Rendert eine datengetriebene [MelodySpec] zu einem nahtlosen PCM-Loop —
 * die parametrisierte Fassung von Kap-Krakenzahns Music.render(). Bewusst
 * eine reine Funktion ohne Android-Abhängigkeit: derselbe Spec ergibt exakt
 * dasselbe PCM, damit sie als schneller JVM-Test prüfbar ist. Dadurch kann
 * die KI zur Laufzeit neue, sanfte Schleifen „komponieren".
 */
object MelodySynth {

    const val SAMPLE_RATE = 22050

    /** Rendert die Melodie zu 16-Bit-Mono-PCM (ein Loop). */
    fun render(spec: MelodySpec): ShortArray {
        val beatSamples = SAMPLE_RATE * 60 / spec.tempoBpm
        val melodyBeats = spec.melody.sumOf { it.dauer }
        val totalBeats = maxOf(melodyBeats, spec.chords.size * 2, 8).coerceAtMost(64)
        val n = totalBeats * beatSamples
        val out = DoubleArray(n)

        renderPads(out, spec, totalBeats, beatSamples)
        renderBass(out, spec, totalBeats, beatSamples)
        renderMelody(out, spec, beatSamples, n)

        return ShortArray(n) { i ->
            (out[i] * Short.MAX_VALUE).toInt().coerceIn(-32768, 32767).toShort()
        }
    }

    /** Getragene Akkordflächen, gleichmäßig über den Loop verteilt. */
    private fun renderPads(out: DoubleArray, spec: MelodySpec, totalBeats: Int, beatSamples: Int) {
        if (spec.chords.isEmpty()) return
        val seg = out.size / spec.chords.size
        val ramp = (SAMPLE_RATE / 2).coerceAtMost(seg / 2).coerceAtLeast(1)
        val padGain = when (spec.timbre) {
            Timbre.ORGEL -> 0.060
            Timbre.GLOCKE -> 0.035
            else -> 0.045
        }
        for (c in spec.chords.indices) {
            val start = c * seg
            for (i in 0 until seg) {
                if (start + i >= out.size) break
                val env = min(1.0, min(i.toDouble() / ramp, (seg - 1 - i).toDouble() / ramp))
                val t = i.toDouble() / SAMPLE_RATE
                var s = 0.0
                for (freq in spec.chords[c].freqs) s += sin(2 * PI * freq * t)
                out[start + i] += s * padGain * env
            }
        }
    }

    /** Ein weicher Basston pro „Takt" (Bass-Liste gleichmäßig verteilt). */
    private fun renderBass(out: DoubleArray, spec: MelodySpec, totalBeats: Int, beatSamples: Int) {
        if (spec.bass.isEmpty()) return
        val seg = out.size / spec.bass.size
        for (b in spec.bass.indices) {
            val start = b * seg
            val freq = midiToFreq(spec.bass[b])
            for (i in 0 until seg) {
                if (start + i >= out.size) break
                val t = i.toDouble() / SAMPLE_RATE
                val env = attack(i) * exp(-2.5 * i / seg)
                out[start + i] += sin(2 * PI * freq * t) * 0.16 * env
            }
        }
    }

    /** Sparsame gezupfte Melodie; Note.midi 0 = Pause. */
    private fun renderMelody(out: DoubleArray, spec: MelodySpec, beatSamples: Int, n: Int) {
        var pos = 0
        val (overtone, decay) = when (spec.timbre) {
            Timbre.GLOCKE -> 0.6 to 3.0
            Timbre.ZUPF -> 0.35 to 6.0
            Timbre.ORGEL -> 0.15 to 1.5
            Timbre.WEICH -> 0.30 to 4.0
        }
        for (note in spec.melody) {
            val len = note.dauer * beatSamples
            if (note.midi != 0) {
                val freq = midiToFreq(note.midi)
                for (i in 0 until len) {
                    if (pos + i >= n) break
                    val t = i.toDouble() / SAMPLE_RATE
                    val env = attack(i) * exp(-decay * i / len)
                    val s = sin(2 * PI * freq * t) + overtone * sin(2 * PI * freq * 2 * t)
                    out[pos + i] += s * 0.12 * env
                }
            }
            pos += len
            if (pos >= n) break
        }
    }

    private fun midiToFreq(midi: Int): Double = 440.0 * 2.0.pow((midi - 69) / 12.0)

    /** Kurzes Einschwingen gegen Knackser (5 ms). */
    private fun attack(i: Int): Double {
        val a = SAMPLE_RATE / 200
        return if (i < a) i.toDouble() / a else 1.0
    }
}
