# Lumen Teleport Lab

Eigenständiges, ausschließlich beobachtendes Diagnosewerkzeug für **Lumen Client**, Minecraft **1.21.7**, Forge **57.0.3**, Java **21**. Die Aufzeichnung ist bei jedem Programmstart **ausgeschaltet**. Keine eigenen Teleports, Bewegungspakete, Paketänderungen, Uploads oder Serverinstallation.

## Installation und Benutzung

1. Java 21 installieren. Im Repository `gradlew.bat build` unter Windows bzw. `./gradlew build` unter Linux ausführen. Der erste Build benötigt Internetzugriff auf Gradle, Forge, Mojang und Sponge.
2. `forge/build/libs/Lumen-Teleport-Lab-1.21.7-1.0.0.jar` neben der bestehenden Lumen-JAR in den `mods`-Ordner der **Forge-1.21.7-Installation** kopieren. Die bisherige Lumen-JAR bleibt erhalten.
3. Im Spiel **F8** drücken (unter Steuerung umbelegbar), **Aufzeichnen** wählen, anschließend einen Teleport über Lumen auslösen. F6-Wegpunkte, F5-letztes Ziel und Zurück-Funktion laufen über denselben Hook.
4. Standardmäßig zehn Sekunden beobachten lassen. Das Dashboard pausiert das Spiel nicht. Während der Messung möglichst stillstehen: normales Weiterlaufen wird als beobachtete Bewegung erfasst.
5. **CSV exportieren** schreibt alle abgeschlossenen, noch gespeicherten Versuche in ein neues Verzeichnis unter `.minecraft/config/teleportlab/exports/teleport-<Zeit>-<UUID>/`. Bei einer eigenen Spielinstallation deren `config`-Ordner verwenden. Eine laufende Beobachtung wird beim Export ausgelassen; **Stoppen** beendet sie vorher mit `UNKNOWN`.
6. Für ChatGPT beide Dateien desselben Exportordners gemeinsam verwenden. Sie sind über `attempt_id` verknüpft. Export ist ein vollständiger Snapshot: wiederholte Exporte enthalten überlappende IDs und müssen bei Zusammenführung dedupliziert werden.

Das Dashboard zeigt Versuche, stabile Erfolge, Korrekturen, Quote, unklare Ergebnisse und mittleren Ping. Die Tabelle ist seitenweise durchsuchbar; **Details** zeigt Koordinaten, Abweichung, Zeitmessungen, Gründe und relevante Ereignisse. Zurücksetzen fragt vor dem Verwerfen der Messdaten nach.

Unter Windows kann auch `teleport_analyser_bauen.bat` gestartet werden. Der Starter baut und testet; nur das ausdrücklich angegebene Argument `-Install` kopiert die Lab-JAR nach `%APPDATA%/.minecraft/mods`. Die Zielplattform steht in `versions/1.21.7.properties`; eine andere Minecraft-Version erfordert zusätzlich eine Anpassung und Prüfung der Mixins.

## Komponenten

| Verzeichnis | Aufgabe |
| --- | --- |
| `core/` | Reiner Java-Kern ohne Minecraft-, Forge- oder Bibliotheksabhängigkeiten: Zustandsautomat, Ereignisse, Messwerte, Statistik, HMAC-Kennung und CSV-Exporter |
| `forge/` | Eigenständige Client-Mod, optionaler Lumen-Mixin, Vanilla-Positionsbeobachter, Sampling, Konfiguration, F8-Dashboard und asynchroner Export |
| `examples/` | Acht **synthetische** Versuche mit allen fünf Status, einschließlich Dimensionswechsel |
| `tools/` | Java-Simulationen ohne Gradle, Pandas-Validierung, optionaler Windows-Build und GitHub-Veröffentlichung |
| `docs/` | Quellcodeanalyse, Integrationsvertrag, CSV-Schema, Messgrenzen und Prüfbericht |

Der Analyzer benötigt **keine Änderungen am Lumen-Quellcode**. Der optionale `@Pseudo`-Mixin beobachtet den Eintritt und die erfolgreiche Rückkehr von `TeleportService.teleportTo`. Er liest den privaten Zielrecord über dessen Accessoren. Abgewiesene Aufrufe erzeugen keinen Versuch. Der Vanilla-Mixin liest die bereits angewendete Serverposition nach `handleMovePlayer`; relative Koordinaten werden von Vanilla aufgelöst. Alle Bestätigungen bleiben bei Vanilla. Details: [Integration](docs/INTEGRATION.md), [Quellcodeanalyse](docs/LUMEN_ANALYSIS.md).

## Bewertung

| Status | Beobachtete Grundlage |
| --- | --- |
| `SUCCESS_STABLE` | Explizite Serverposition am Ziel; alle Kontrollpunkte am Ziel; nach erster Ankunft im Zielbereich geblieben; keine Korrektur oder Datenlücke; am Ende mindestens eine Sekunde stabil |
| `CORRECTED` | Mindestens eine von Vanilla angewendete Serverposition außerhalb des Zielbereichs im Beobachtungsfenster |
| `PARTIAL` | Bewegung ohne vollständige stabile Zielankunft, verpasste frühe Ankunft oder zwischenzeitliches Verlassen des Zielbereichs |
| `TIMEOUT` | Keine eindeutige Bestätigung, insbesondere nur lokal sichtbares Ziel ohne Serverpositionsbeleg |
| `UNKNOWN` | Abbruch, neue Sitzung, Tod, Aufzeichnungsstopp, überlappender Versuch oder unzureichende zeitliche Abdeckung |

Abbrüche haben Vorrang vor anderen Status; bereits beobachtete Korrekturen bleiben im Zähler und Ereignisprotokoll erhalten. Ohne Abbruch haben Korrekturen Vorrang vor einer Abdeckungslücke. Quote = stabile Erfolge / **alle abgeschlossenen** gespeicherten Versuche, einschließlich `UNKNOWN` und `TIMEOUT`. Laufende Versuche zählen in der Gesamtzahl, aber noch nicht im Quotennenner. „Unklar“ = `UNKNOWN` + `TIMEOUT`.

`SUCCESS_STABLE` beschreibt ausschließlich Evidenz **innerhalb des gemessenen Fensters**. Ein Client kann keine dauerhafte serverinterne Entscheidung oder zukünftige Rücksetzung beweisen. Viele Server senden bei normaler Bewegung keine explizite Positionsbestätigung: ein real funktionierender Teleport kann deshalb konservativ `TIMEOUT` bleiben. [Messgrenzen](docs/MEASUREMENT_LIMITS.md).

## Konfiguration

Beim ersten Start entsteht `config/teleportlab/lab.properties`. Änderungen werden nach Minecraft-Neustart wirksam:

```properties
observation_ms=10000
checkpoints_ms=1000,3000,5000,10000
target_tolerance_blocks=1.5
stationary_tolerance_blocks=0.10
stable_ms=1000
max_sample_gap_ms=500
max_attempts=500
```

Kontrollpunkte müssen streng aufsteigen und mit `observation_ms` enden. Fenster: 1–120 Sekunden. Der Positionsverlauf wird pro Client-Tick abgetastet (normal etwa 20 Hz); Pausen und lange Hänger können `UNKNOWN` verursachen. Ereignisse speichern tatsächliche Messzeiten; verpasste Zeitpunkte werden nicht rückdatiert. Bei ungültiger/nicht lesbarer Konfiguration zeigt das Dashboard die Verwendung von Standardwerten an.

Messdaten liegen bis zum Export im Arbeitsspeicher. Standardmäßig werden die letzten 500 abgeschlossenen Versuche gehalten; ältere fallen heraus und der Zähler im Dashboard macht dies sichtbar. Neustart und Zurücksetzen verwerfen nicht exportierte Messdaten. Für größere Serien regelmäßig exportieren; längere Fenster und höhere Limits brauchen mehr Speicher.

## Datenschutz und CSV

UTF-8 ohne BOM, Komma als Trenner, CRLF, standardkonforme Anführungszeichen, Dezimalpunkt, festes Schema v1 und UTC mit Millisekunden. Fehlende Messwerte bleiben leer; Dimensionswechsel erhalten keine sinnlose dimensionenübergreifende Distanz. [Alle Spalten und Einheiten](docs/CSV_SCHEMA.md).

Der Export enthält keine Spielernamen, Spieler-UUIDs, Wegpunktnamen, Serveradressen, Zugangsdaten oder Welt-/Dateipfade. Server werden über HMAC-SHA256 mit einem lokalen Zufallsschlüssel pseudonymisiert. **`config/teleportlab/server-hmac.key` niemals weitergeben**; er gehört nicht zum Export. Andere Installationen haben andere Kennungen. Alle lokalen Einzelspielerwelten teilen absichtlich dieselbe pseudonyme Serverkennung. Koordinaten und Dimensionen sind Forschungsdaten und können Bauorte verraten; nur die bewusst ausgewählten CSV-Dateien teilen.

```python
import pandas as pd
a = pd.read_csv('teleport_attempts.csv')
e = pd.read_csv('teleport_events.csv')
a['started_utc'] = pd.to_datetime(a['started_utc'], utc=True)
print(a.groupby('status').size())
print(a.groupby('status')[['total_distance_blocks', 'average_ping_ms']].mean())
```

## Entwicklung und Tests

```powershell
.\gradlew.bat build
python -m pip install -r tools/requirements.txt
python tools/validate_csv.py
# Reiner Kern ohne Forge-Konfiguration:
.\gradlew.bat :core:check -PcoreOnly
# Java-Simulationen + Pandas ganz ohne Gradle:
.\tools\test-core.ps1
# Optional für eingeschränkte Windows-Umgebungen mit blockiertem Gradle-Loopback:
.\tools\build-restricted.ps1 -Offline
```

`-Offline` funktioniert nach einem erfolgreichen Download aller Abhängigkeiten. ForgeGradle kann trotzdem beim Konfigurieren einen Warn-Stacktrace für Mojangs Metadaten ausgeben; maßgeblich ist der abschließende Buildstatus. Entwicklungsclient: `gradlew.bat :forge:runClient`; zum Test neben Lumen dessen bestehende JAR in `forge/run/mods` legen.

Simulationen prüfen stabile Ankunft, fehlende Serverbelege, späte Rollbacks, verzögerte Korrekturen, Teilbewegung, Timeout, Disconnect, Sitzungs-/Dimensionswechsel, Messlücken, aufeinanderfolgende Versuche, Uhrzeitsprünge, Speicherlimit, Pseudonymisierung und CSV-Escaping. Pandas prüft zusätzlich Schema, Typen, IDs, Zeitreihenfolge und alle Status. [Prüfbericht und offene Laufzeitprüfungen](docs/VERIFICATION.md).

Das Repository enthält keine kopierten Lumen-Quellen. Untersucht wurde [ILinIky/Minecraft-Lumen-Client](https://github.com/ILinIky/Minecraft-Lumen-Client), Commit `4e6442fbc3cb126534491d42eb28eda86a850060`. Der Hook ist versionsgebunden; nach Änderungen an Lumen bitte die Integrationsprüfung wiederholen. Für neu erzeugten Code werden hier keine weitergehenden Lizenzrechte eingeräumt; Gradle-Wrapper-Lizenzhinweise sind unter `gradle/` enthalten.

