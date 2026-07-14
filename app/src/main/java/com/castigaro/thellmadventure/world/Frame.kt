package com.castigaro.thellmadventure.world

/**
 * Der feste RAHMEN des Spiels: die drei Basis-Storys mit ihren verdeckten
 * Intake-Fragen. Die Fragen sind als In-Fiction-Momente getarnt — der
 * Spieler merkt nicht, dass er gerade seine Geschichte konfiguriert.
 * Die Antwort-Tags fließen in die Story-Synthese ein und mischen aus dem
 * Motiv-Subset eine neue Geschichte, die für die Session fixiert wird.
 */
object Frame {

    class IntakeOption(val label: String, val tag: String)

    class IntakeQuestion(val text: String, val options: List<IntakeOption>)

    class BaseStory(
        val id: String,
        val titel: String,
        val untertitel: String,
        /** Setting-Karte für den Synthese-Prompt. */
        val settingKarte: String,
        /** Motiv-Subset („Teilstories"), aus dem die Synthese mischt. */
        val motive: List<String>,
        val intake: List<IntakeQuestion>,
    )

    val BASIS_STORYS: List<BaseStory> = listOf(
        BaseStory(
            id = "pirateninsel",
            titel = "Pirateninsel",
            untertitel = "Gestrandet zwischen Palmen, Wracks und alten Flüchen.",
            settingKarte = "Eine abgelegene Pirateninsel: Strände, Dschungel, Schmugglerhöhlen, " +
                "ein halb versunkenes Wrack. Salz, Teer und Geheimnisse in der Luft. " +
                "Technik-Level: Segelzeit. Stimmung zwischen Abenteuer und leiser Melancholie.",
            motive = listOf(
                "eine Meuterei, die nie aufgeklärt wurde",
                "eine Karte, die sich selbst verändert",
                "ein Seeungeheuer, das Angst vor etwas hat",
                "Schmuggler mit einem seltsamen Ehrenkodex",
                "ein Leuchtfeuer, das niemand anzündet",
            ),
            intake = listOf(
                IntakeQuestion(
                    "Die Welle hat dich an Land gespült. Was hältst du noch umklammert?",
                    listOf(
                        IntakeOption("Ein rostiges Entermesser", "kämpferisch"),
                        IntakeOption("Das Logbuch des Käpt'ns", "rätselnd"),
                        IntakeOption("Einen zwitschernden Kompass", "wunderlich"),
                    ),
                ),
                IntakeQuestion(
                    "Aus dem Dickicht ruft eine Stimme deinen Namen. Du …",
                    listOf(
                        IntakeOption("rufst zurück", "gesellig"),
                        IntakeOption("duckst dich ins Schilf", "vorsichtig"),
                        IntakeOption("gehst dem Klang nach", "neugierig"),
                    ),
                ),
                IntakeQuestion(
                    "Die Sonne sinkt. Wovon träumst du am Feuer?",
                    listOf(
                        IntakeOption("Von Gold, das niemandem fehlt", "schatzsuche"),
                        IntakeOption("Von einem Hafen, der Heimat heißt", "heimkehr"),
                        IntakeOption("Von einer offenen Rechnung", "vergeltung"),
                    ),
                ),
            ),
        ),
        BaseStory(
            id = "mondstation",
            titel = "Mondstation",
            untertitel = "Allein erwacht in einer stillen Station über der Erde.",
            settingKarte = "Eine Forschungsstation auf dem Mond: Korridore, Labore, Hydroponik, " +
                "ein Funkraum mit toten Lampen. Draußen Staub und Sterne. " +
                "Technik-Level: nahe Zukunft. Stimmung zwischen Staunen und sanfter Unruhe.",
            motive = listOf(
                "eine KI, die höflich lügt",
                "ein Gewächshaus, das nachts wächst",
                "eine Crew, die nie eingecheckt hat",
                "ein Signal von der dunklen Seite",
                "ein Vorrat, der für zwei reicht — du bist allein",
            ),
            intake = listOf(
                IntakeQuestion(
                    "Der Funkspruch knackt: „Wen sollen wir wecken?“ Du flüsterst …",
                    listOf(
                        IntakeOption("„Die Technikerin.“", "praktisch"),
                        IntakeOption("„Den Botaniker.“", "behutsam"),
                        IntakeOption("„Die Kommandantin.“", "entschlossen"),
                    ),
                ),
                IntakeQuestion(
                    "Im Korridor flackert das Licht. Zuerst prüfst du …",
                    listOf(
                        IntakeOption("die Luftwerte", "vorsichtig"),
                        IntakeOption("das Stationslogbuch", "rätselnd"),
                        IntakeOption("die Werkzeugkiste", "praktisch"),
                    ),
                ),
                IntakeQuestion(
                    "Hinter dem Bullauge hängt die Erde. Was fühlst du?",
                    listOf(
                        IntakeOption("Heimweh", "heimkehr"),
                        IntakeOption("Ruhe", "gelassen"),
                        IntakeOption("Trotz", "eigensinnig"),
                    ),
                ),
            ),
        ),
        BaseStory(
            id = "hochhaus",
            titel = "Hochhaus",
            untertitel = "Ein Haus ohne oberstes Stockwerk. Du kennst nur deins.",
            settingKarte = "Ein endloses Hochhaus: Flure mit Zeitungsstapeln, ein launischer " +
                "Aufzug, Türen mit Nummern, die nicht zählen wollen. " +
                "Technik-Level: Gegenwart mit Rissen. Stimmung: Alltagsmagie, leicht unheimlich, warmherzig.",
            motive = listOf(
                "ein Stockwerk, das nur dienstags existiert",
                "eine Hausverwaltung, die niemand je sah",
                "Briefe, die vor dem Absender ankommen",
                "ein Nachbar, der Erinnerungen tauscht",
                "der 13. Stock, den alle leugnen",
            ),
            intake = listOf(
                IntakeQuestion(
                    "Der Aufzugknopf glüht. Welche Richtung ruft dich?",
                    listOf(
                        IntakeOption("Nach oben", "ehrgeizig"),
                        IntakeOption("Nach unten", "geheimnisvoll"),
                        IntakeOption("Der Knopf ohne Zahl", "wunderlich"),
                    ),
                ),
                IntakeQuestion(
                    "In deinem Briefkasten liegt heute …",
                    listOf(
                        IntakeOption("ein Schlüssel ohne Anhänger", "neugierig"),
                        IntakeOption("eine Rechnung mit falschem Namen", "rätselnd"),
                        IntakeOption("das Foto einer Tür", "entschlossen"),
                    ),
                ),
                IntakeQuestion(
                    "Die Nachbarin nickt dir zu. Du …",
                    listOf(
                        IntakeOption("grüßt freundlich", "gesellig"),
                        IntakeOption("fragst nach dem 13. Stock", "direkt"),
                        IntakeOption("tust, als sähst du nichts", "vorsichtig"),
                    ),
                ),
            ),
        ),
    )

    fun baseStory(id: String): BaseStory =
        BASIS_STORYS.firstOrNull { it.id == id } ?: BASIS_STORYS.first()

    /** Katalog-Beschreibung für die System-Prompts (was die KI wählen darf). */
    fun katalogText(): String = buildString {
        appendLine("KATALOG (nur daraus wählen, Werte kleingeschrieben):")
        appendLine("Requisiten-Typen: " + PropType.values().joinToString(", ") { it.name.lowercase() })
        appendLine("Paletten: " + Palette.values().joinToString(", ") { it.name.lowercase() })
        appendLine("Icon-Formen: " + IconShape.values().joinToString(", ") { it.name.lowercase() })
        appendLine("Körperformen: " + BodyShape.values().joinToString(", ") { it.name.lowercase() })
        appendLine("Kopfbedeckungen: " + Headgear.values().joinToString(", ") { it.name.lowercase() })
        appendLine("Zustände: " + PROP_STATES.joinToString(", "))
        appendLine("Farb-Slots: 0..7, Wände: n, o, s, w")
        append(
            "Raumgröße ${Limits.MIN_RAUM_W}..${Limits.MAX_RAUM_W} × " +
                "${Limits.MIN_RAUM_H}..${Limits.MAX_RAUM_H} Kacheln, " +
                "max ${Limits.MAX_PROPS} Requisiten, max ${Limits.MAX_NPCS} Figuren, " +
                "${Limits.MIN_TUEREN}..${Limits.MAX_TUEREN} Türen je Raum."
        )
    }
}
