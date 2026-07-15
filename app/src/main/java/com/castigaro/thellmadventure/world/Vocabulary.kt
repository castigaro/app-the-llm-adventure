package com.castigaro.thellmadventure.world

/**
 * Das FESTE Vokabular des Spiels — der Katalog, aus dem die KI wählen darf.
 * Alles außerhalb dieser Enums wird vom ContentValidator verworfen. Das ist
 * die eine Hälfte des Sicherheitsmodells (die andere ist die Effekt-Whitelist):
 * Die KI kann Inhalte ERSCHAFFEN, aber nur aus bekannten Bausteinen.
 */

/** Wandseiten eines Raums (Norden/Osten/Süden/Westen). */
enum class Wall { N, O, S, W }

/**
 * Farbstimmung eines Raums. Jede Palette liefert alle Töne, die der Renderer
 * braucht — die KI wählt nur den Namen.
 */
enum class Palette(
    val bgTop: Int, val bgBottom: Int,
    val floorA: Int, val floorB: Int,
    val wallLight: Int, val wallDark: Int,
    val accent: Int,
) {
    ROST(0xFF3A2A22.toInt(), 0xFF1E1512.toInt(), 0xFF8A5A34.toInt(), 0xFF7A4C2A.toInt(), 0xFF9C6B42.toInt(), 0xFF5E3B21.toInt(), 0xFFE0A73C.toInt()),
    STAHL(0xFF2A3440.toInt(), 0xFF161C24.toInt(), 0xFF6E7B8A.toInt(), 0xFF5E6A78.toInt(), 0xFF8494A6.toInt(), 0xFF46525E.toInt(), 0xFF7FC7E8.toInt()),
    SAND(0xFF4A3E2A.toInt(), 0xFF2A2318.toInt(), 0xFFE8D2A0.toInt(), 0xFFD8BE88.toInt(), 0xFFF0E0B8.toInt(), 0xFFB89A64.toInt(), 0xFF1C6E8C.toInt()),
    MOOS(0xFF22321E.toInt(), 0xFF121A10.toInt(), 0xFF2F7D46.toInt(), 0xFF286B3C.toInt(), 0xFF4A9A5E.toInt(), 0xFF1E5730.toInt(), 0xFFE0A73C.toInt()),
    NACHT(0xFF141A2E.toInt(), 0xFF0A0E1A.toInt(), 0xFF2E3550.toInt(), 0xFF272D44.toInt(), 0xFF3E4668.toInt(), 0xFF1E2338.toInt(), 0xFF7FC7E8.toInt()),
    KORALLE(0xFF3E2226.toInt(), 0xFF221214.toInt(), 0xFFB05A50.toInt(), 0xFF9C4C44.toInt(), 0xFFC87468.toInt(), 0xFF7A3A34.toInt(), 0xFFF0E0B8.toInt()),
    EIS(0xFF2A3E46.toInt(), 0xFF16222A.toInt(), 0xFFB8D8E0.toInt(), 0xFFA0C4D0.toInt(), 0xFFD0E8F0.toInt(), 0xFF7AA0B0.toInt(), 0xFF2E4A7A.toInt()),
    PURPUR(0xFF2E1E3A.toInt(), 0xFF180F20.toInt(), 0xFF6E4A8A.toInt(), 0xFF5E3E78.toInt(), 0xFF8A64A8.toInt(), 0xFF44285A.toInt(), 0xFFE0A73C.toInt()),
}

/**
 * Requisiten-Typen. Der PropPainter kennt für jeden Typ eine eigene
 * prozedurale Silhouette — es gibt keine Bild-Assets.
 */
enum class PropType {
    KISTE, TRUHE, FASS, REGAL, TISCH, STUHL, BETT, OFEN, KAMIN,
    TERMINAL, KONSOLE, HEBEL, ROHR, LAMPE, STATUE, BRUNNEN, KESSEL,
    PFLANZE, KAKTUS, PILZ, KRISTALL, STEIN, TEPPICH, SCHILD, SPIEGEL,
    ALTAR, ANKER, SEIL, LEITER, UHR,
}

/** Erlaubte Zustands-Schlüssel an Requisiten. */
val PROP_STATES = setOf("offen", "an", "kaputt", "verschlossen")

/**
 * Icon-Silhouetten für Gegenstände und Fähigkeiten.
 * Alt-Vokabular: neue Icons wählen ein [IconArchetype]; die Form bleibt nur
 * erhalten, damit gespeicherte Spielstände weiter lesbar sind.
 */
enum class IconShape {
    KREIS, RAUTE, FLASCHE, SCHLUESSEL, ZAHNRAD, ROLLE,
    STERN, TROPFEN, SCHILD, WUERFEL, BLITZ, HERZ,
}

/**
 * Detail-Motive für Gegenstands- und Fähigkeits-Icons. Der IconPainter kennt
 * für jedes Motiv eine eigene ausgearbeitete Vektorzeichnung (Stil-Vorbild:
 * Kap Krakenzahns ItemIconPainter) — die KI wählt nur das passendste Motiv.
 */
enum class IconArchetype {
    SCHLUESSEL, FLASCHE, BUCH, SCHRIFTROLLE, WERKZEUG, KLINGE, FACKEL,
    LATERNE, SEIL, HAKEN, ZAHNRAD, KRISTALL, MUENZE, AMULETT, RING,
    KARTE, KOMPASS, HORN, FEDER, KRAUT, PILZ, KNOCHEN, BEUTEL, KRUG,
    SPIEGEL, GLOCKE, STEIN, WUERFEL,
}

/** Material eines Icons — bestimmt die Grundfarb-Rampe des Motivs. */
enum class IconMaterial(val grund: Int) {
    HOLZ(0xFF8A5A34.toInt()),
    EISEN(0xFF6E7B8A.toInt()),
    MESSING(0xFFC9973C.toInt()),
    STEIN(0xFF8E8E96.toInt()),
    GLAS(0xFF9CC8DC.toInt()),
    KRISTALL(0xFF9C7CD8.toInt()),
    STOFF(0xFFB05A50.toInt()),
    KNOCHEN(0xFFE8E0CC.toInt()),
}

/** Körperformen für Figuren (Spieler und NPCs). */
enum class BodyShape { TRAPEZ, ROBE, BREIT, SCHLANK, RUND, KANTIG }

/** Kopfbedeckungen für Figuren. */
enum class Headgear { KEINE, HUT, KAPUZE, HELM, KRONE, ANTENNE }

/** Klangfarben für generierte Melodien. */
enum class Timbre { WEICH, GLOCKE, ZUPF, ORGEL }

/** Akkorde mit festen Frequenzen (Muster aus Kap-Krakenzahns Music.kt). */
enum class Chord(val freqs: DoubleArray) {
    C(doubleArrayOf(261.63, 329.63, 392.00)),
    DM(doubleArrayOf(293.66, 349.23, 440.00)),
    EM(doubleArrayOf(329.63, 392.00, 493.88)),
    F(doubleArrayOf(349.23, 440.00, 523.25)),
    G(doubleArrayOf(392.00, 493.88, 587.33)),
    AM(doubleArrayOf(220.00, 261.63, 329.63)),
}

/** Maschinell prüfbare Typen des geheimen Ziels. */
enum class GoalType { BESITZE_ITEM, ERREICHE_RAUM, ZUSTAND_GESETZT }

/** Feste Farbpalette für Figuren und Icons (Index = "Farb-Slot"). */
val SLOT_COLORS = intArrayOf(
    0xFF9C2B2B.toInt(), // Rot
    0xFF2E5A9C.toInt(), // Blau
    0xFF2F7D46.toInt(), // Grün
    0xFFE0A73C.toInt(), // Gold
    0xFF6E4A8A.toInt(), // Violett
    0xFF1C6E8C.toInt(), // Petrol
    0xFF8A5A34.toInt(), // Braun
    0xFF6E7B8A.toInt(), // Grau
)

/** Grundregeln des Rahmens — was maximal möglich ist. */
object Limits {
    const val MAX_RAEUME = 24
    /** Das Zielobjekt darf frühestens ab so vielen Räumen versteckt werden. */
    const val ZIEL_MIN_RAEUME = 6
    const val MIN_RAUM_W = 6
    const val MAX_RAUM_W = 16
    const val MIN_RAUM_H = 4
    const val MAX_RAUM_H = 12
    const val MAX_PROPS = 12
    const val MAX_NPCS = 2
    const val MIN_TUEREN = 1
    const val MAX_TUEREN = 4
    const val MAX_INVENTAR = 12
    const val MAX_FAKTEN_DIGEST = 20
}
