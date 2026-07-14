package com.castigaro.thellmadventure.audio

import com.castigaro.thellmadventure.world.Chord
import com.castigaro.thellmadventure.world.MelodySpec
import com.castigaro.thellmadventure.world.Note
import com.castigaro.thellmadventure.world.Timbre
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MelodySynthTest {

    private fun spec(id: String = "t", tempo: Int = 90) = MelodySpec(
        id = id, tempoBpm = tempo, timbre = Timbre.WEICH,
        chords = listOf(Chord.C, Chord.AM, Chord.F, Chord.G),
        bass = listOf(48, 45, 41, 43),
        melody = listOf(Note(64, 1), Note(67, 1), Note(0, 1), Note(72, 1)),
    )

    @Test
    fun `gleiche Spec ergibt identisches PCM (deterministisch)`() {
        val a = MelodySynth.render(spec())
        val b = MelodySynth.render(spec())
        assertArrayEquals(a, b)
    }

    @Test
    fun `Loop-Laenge entspricht Takten mal Beat-Samples`() {
        val tempo = 90
        val pcm = MelodySynth.render(spec(tempo = tempo))
        val beatSamples = MelodySynth.SAMPLE_RATE * 60 / tempo
        // totalBeats = max(melodyBeats=4, chords*2=8, 8) = 8
        assertEquals(8 * beatSamples, pcm.size)
    }

    @Test
    fun `alle eingebauten Melodien rendern ohne Absturz und mit Inhalt`() {
        Melodies.BUILTINS.forEach { melody ->
            val pcm = MelodySynth.render(melody)
            assertTrue("${melody.id} sollte klingen", pcm.isNotEmpty())
            assertTrue("${melody.id} sollte nicht nur Stille sein", pcm.any { it.toInt() != 0 })
        }
    }

    @Test
    fun `forRoom ist deterministisch fuer dieselbe Raum-Id`() {
        assertEquals(Melodies.forRoom("r7").id, Melodies.forRoom("r7").id)
    }

    @Test
    fun `leere Melodie ergibt trotzdem einen hoerbaren Pad-Loop`() {
        val nurPads = MelodySpec(
            id = "p", tempoBpm = 80, timbre = Timbre.ORGEL,
            chords = listOf(Chord.C, Chord.G), bass = emptyList(), melody = emptyList(),
        )
        val pcm = MelodySynth.render(nurPads)
        assertTrue(pcm.isNotEmpty())
        assertTrue(pcm.any { it.toInt() != 0 })
    }
}
