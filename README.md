# The LLM Adventure

Ein isometrisches Android-Adventure, das sich **während des Spielens selbst
erschafft**. KI und Mensch bauen die Welt gemeinsam: Räume, Figuren,
Gegenstände, Rätsel und ihre Lösungen entstehen erst beim Spielen — und
bleiben dann bestehen. Jede Partie ist anders.

## Idee

Es gibt eine schlichte Ausgangsbasis (ein isometrischer Raum). Jede Tür ins
Ungewisse öffnet einen Raum, den die KI **im Moment des Durchschreitens**
erfindet und der sich danach dauerhaft manifestiert. Der Spieler tippt nie
freien Text — die KI erzeugt in Echtzeit auswählbare Antwortmöglichkeiten.
Sie filtert dabei nach Sinnhaftigkeit: Es ist **nicht möglich, das Spiel
durch eigene Eingaben kaputtzumachen**.

Zu Beginn wählt man eine von drei Grundstorys (Pirateninsel, Mondstation,
Hochhaus). Ein paar verdeckte, als In-Fiction-Momente getarnte Fragen weben
daraus eine neue Geschichte mit einem **geheimen Ziel**, das für die ganze
Spielsession fest bleibt.

## Bausteine

- **Isometrischer Renderer mit Kamera** — Räume dürfen größer als das Display
  sein und werden gescrollt (Ein-Finger-Ziehen schwenkt, ⌖ zentriert).
  Alles ist prozedural gezeichnet, ohne Grafik-Assets, damit die KI Inhalte
  als reine Daten erschaffen kann.
- **Zwei Sicherheitskanäle** — die KI darf (a) nur vorab freigegebene,
  zustandsgeprüfte Effekte auslösen (Whitelist) und (b) neue Inhalte nur als
  streng gegen ein festes Vokabular validiertes, in den Rahmen geklemmtes
  JSON erschaffen.
- **Crafting** — 2–3 Gegenstände lassen sich in einem Minecraft-artigen
  Raster zu einem neuen verbinden; der wird mit Objekten im Raum benutzbar.
  Aufgesammelte Gegenstände verschwinden aus dem Raum.
- **Kompaktes Weltgedächtnis** — der Spielstand wird in der kompaktesten Form
  gehalten, damit die KI tokensparend darauf zugreifen und sich merken kann,
  was es schon gibt.
- **Musik je Raum** — sanfte, datengetriebene „MIDI"-Schleifen; eingebaute
  Melodien plus solche, die die KI zur Laufzeit komponiert. In den
  Spiel-Einstellungen abschaltbar.
- **Getrennte Menüs** — App-Menü (Provider/API-Key, Update) auf dem
  Startbildschirm, Spielmenü (Speichern, Als neue Geschichte sichern,
  Spiel-Einstellungen, Vollbild) im laufenden Spiel.

## API-Key nötig

Das Spiel lebt von der KI-Generierung — es gibt bewusst **keinen
Offline-Modus**. Ohne hinterlegten Anthropic- oder OpenAI-Key führt der
Spielstart zu einem Einrichtungsbildschirm. Die Key-Verwaltung stammt aus der
gemeinsamen Bibliothek
[`android-apps-common`](https://github.com/appsonar/android-apps-common)
(`com.appsonar.common.llm.ProviderSettings`); der Key bleibt auf dem Gerät,
die geschätzten Kosten werden mitgezählt.

## Speichern

Es kann jederzeit gespeichert werden (Autosave nach jeder Aktion). Der
aktuelle Weltzustand lässt sich außerdem als **neue, wiederspielbare
Geschichte** sichern — beim erneuten Spielen entwickeln sich unentdeckte
Räume frisch, sodass sich Durchläufe unterscheiden. Jede Partie liegt als
eigene JSON-Datei unter `files/saves/`.

## Technik

Native Android-App (Kotlin, Gradle KTS, minSdk 26 / compileSdk 34, JDK 17,
ViewBinding). Bindet `:common` über `../android-apps-common/android` ein
(Geschwister-Repo). LLM-Zugriff
per OkHttp direkt gegen die Anthropic- (Messages API) bzw. OpenAI-Endpunkte,
mit optionalen Structured Outputs und tolerantem JSON-Parser als Rückfall.

### Tests

JVM-Unit-Tests (JUnit 4 + Robolectric + OkHttp MockWebServer) unter
`app/src/test/`. Ausführen über den Play-Button `the-llm-adventure:test` im
Entwicklungs-Cockpit oder direkt:

```
./gradlew :app:testDebugUnitTest
```

Geprüft werden u. a. die isometrische Mathematik, die Pfadfindung, der
Content-Validator (Clamps, Rücktür-Erzwingung, Begehbarkeit), die
Effekt-Whitelist, der kompakte Zustands-Digest, die API-Anbindung (mit
MockWebServer), das Crafting (Cache/Unmöglich), die Persistenz und die
deterministische Melodie-Synthese.

## Lizenz

Copyright © 2026 Torsten Klein

Dieses Projekt steht unter der **GNU Affero General Public License v3.0 oder
später** (AGPL-3.0-or-later), siehe [LICENSE](LICENSE): Wer den Code — auch als
Netzwerkdienst — weiterverwendet oder verändert, muss den Quellcode unter
derselben Lizenz offenlegen. Eine kommerzielle Lizenz ist auf Anfrage möglich.
