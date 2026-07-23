package com.appsonar.thellmadventure.audio

import com.appsonar.thellmadventure.world.Chord
import com.appsonar.thellmadventure.world.MelodySpec
import com.appsonar.thellmadventure.world.Note
import com.appsonar.thellmadventure.world.Timbre

/**
 * Die eingebauten, sanft klingenden Grundmelodien — als Daten, damit sie
 * durch denselben Synth laufen wie die von der KI erzeugten. Räume ohne
 * eigene Melodie bekommen deterministisch eine davon zugewiesen.
 */
object Melodies {

    private fun n(midi: Int, dauer: Int = 1) = Note(midi, dauer)

    val BUILTINS: List<MelodySpec> = listOf(
        // 0: sanfter Walzer (C-Dur, wie Kap-Krakenzahn)
        MelodySpec(
            id = "b0", tempoBpm = 90, timbre = Timbre.WEICH,
            chords = listOf(Chord.C, Chord.AM, Chord.F, Chord.G),
            bass = listOf(48, 45, 41, 43),
            melody = listOf(
                n(64), n(67), n(0), n(69), n(67), n(64),
                n(72), n(0), n(69), n(67), n(64), n(0),
            ),
        ),
        // 1: Mond-Pad (getragen, wenig Melodie)
        MelodySpec(
            id = "b1", tempoBpm = 66, timbre = Timbre.ORGEL,
            chords = listOf(Chord.AM, Chord.F, Chord.C, Chord.G),
            bass = listOf(45, 41, 48, 43),
            melody = listOf(n(72, 2), n(0, 1), n(76, 2), n(74, 1), n(69, 2)),
        ),
        // 2: Hafen-Zupf (freundlich, wiegend)
        MelodySpec(
            id = "b2", tempoBpm = 100, timbre = Timbre.ZUPF,
            chords = listOf(Chord.C, Chord.G, Chord.AM, Chord.F),
            bass = listOf(48, 43, 45, 41),
            melody = listOf(n(60), n(64), n(67), n(64), n(65), n(67), n(69), n(67)),
        ),
        // 3: Korridor-Puls (ruhig, gleichmäßig)
        MelodySpec(
            id = "b3", tempoBpm = 84, timbre = Timbre.WEICH,
            chords = listOf(Chord.DM, Chord.AM, Chord.F, Chord.C),
            bass = listOf(50, 45, 41, 48),
            melody = listOf(n(62), n(0), n(65), n(0), n(69), n(0), n(65), n(62)),
        ),
        // 4: Nacht-Glocke (sparsam, glockig)
        MelodySpec(
            id = "b4", tempoBpm = 72, timbre = Timbre.GLOCKE,
            chords = listOf(Chord.EM, Chord.C, Chord.G, Chord.AM),
            bass = listOf(52, 48, 43, 45),
            melody = listOf(n(76, 2), n(72, 2), n(74, 2), n(67, 2)),
        ),
        // 5: Finale (etwas heller, aufsteigend)
        MelodySpec(
            id = "b5", tempoBpm = 96, timbre = Timbre.ZUPF,
            chords = listOf(Chord.F, Chord.G, Chord.C, Chord.C),
            bass = listOf(41, 43, 48, 48),
            melody = listOf(n(65), n(67), n(69), n(72), n(74), n(72), n(76, 2)),
        ),
    )

    /** Eine deterministisch gewählte eingebaute Melodie für einen Raum. */
    fun forRoom(roomId: String): MelodySpec =
        BUILTINS[Math.floorMod(roomId.hashCode(), BUILTINS.size)]
}
