package com.castigaro.thellmadventure.render

import com.castigaro.thellmadventure.world.Prop
import com.castigaro.thellmadventure.world.PropType

/**
 * Entscheidet, wie die IsoView nach einem Frame weitertickt — bewusst als
 * reine Funktionen ohne Android-Abhängigkeit, damit die Logik als schneller
 * JVM-Test prüfbar bleibt. Vor dieser Schleife lief die View permanent mit
 * vsync, auch wenn nichts in Bewegung war.
 */
object FrameGate {

    enum class Modus {
        /** Volle Bildrate: Spieler läuft, Kamera zieht nach oder Geste aktiv. */
        VSYNC,

        /** Gemächlicher Takt (~8 fps) für sichtbares Geschimmer und Wasser. */
        AMBIENT,

        /** Stillstand: kein Selbst-Invalidieren, nächster Frame kommt von außen. */
        IDLE,
    }

    fun modus(bewegung: Boolean, kameraRuht: Boolean, ambientSichtbar: Boolean): Modus = when {
        bewegung || !kameraRuht -> Modus.VSYNC
        ambientSichtbar -> Modus.AMBIENT
        else -> Modus.IDLE
    }

    /**
     * true, wenn der PropPainter diese Requisite zeitgetrieben animiert
     * (Glut, Flamme, Blinken, Pulsieren) oder ein Gegenstands-Funkeln zeigt —
     * nur solche Requisiten halten den Ambient-Takt wach.
     */
    fun propAnimiert(prop: Prop): Boolean {
        if (prop.itemId != null) return true // Funkeln
        return when (prop.type) {
            PropType.OFEN, PropType.KAMIN, PropType.TERMINAL, PropType.KONSOLE ->
                prop.states["an"] == true
            PropType.LAMPE -> prop.states["an"] != false // leuchtet standardmäßig
            PropType.KRISTALL -> true // pulsiert immer
            else -> false
        }
    }
}
