# Integration und Architektur

## Datenfluss

```text
Lumen F6 / F5 / Zurück
  → unverändertes TeleportService.teleportTo
      HEAD: Lab merkt Ursprung, Ziel und beide Uhren
      RETURN true: Lab beginnt Versuch; false: Daten verwerfen
  → ClientTickEvent.Post: tatsächliche Position + vorhandener PlayerInfo-Ping
  → Vanilla handleMovePlayer RETURN: bereits aufgelöste Serverposition
  → Java-Kern: Kontrollpunkte, Verlauf, Korrekturen und finale Bewertung
  → F8 Screen: Statistiken / Details
  → manueller Export: unveränderlicher Snapshot → CSV-Hintergrundthread
```

`core` hängt ausschließlich vom JDK 21 ab. `forge` bettet die Core-Klassen in seine Mod-JAR ein. Zum Installieren wird nur **eine Lab-JAR** benötigt. Serverinstallation und gemeinsame Netzwerkkanäle entfallen. Forge-Metadaten verwenden `clientSideOnly=true` und eine optionale, nachgelagerte Abhängigkeit zur bestehenden Lumen-Mod-ID `examplemod`.

## Optionaler Lumen-Adapter

`LumenTeleportMixin` verwendet `@Pseudo` mit dem String-Ziel `com.example.examplemod.waypoint.TeleportService`. Der Code muss Lumen beim Kompilieren nicht laden. Beide Injektionen hängen an der vorhandenen privaten Methode `teleportTo`. Ihre `Location`-Instanz wird als `@Coerce Object` entgegengenommen; ausschließlich die Record-Accessoren `dimension`, `x`, `y`, `z` werden gelesen. Das Wegpunktlabel wird bewusst ignoriert.

Methodeneintritt erfasst Ursprung, Ziel, UTC und monotone Zeit. Erst `RETURN true` veröffentlicht den Versuch. Ein erster Sample direkt danach protokolliert das bereits erfolgte lokale Umsetzen; bei Einzelspieler kann das Ziel erst später eintreffen. `first_local_arrival_ms` ist der erste **beobachtete** Zeitpunkt, kein exakter Minecraft-interner Schreibzeitpunkt.

Der optionale Hook hat `require=0`: ohne Lumen oder nach einer inkompatiblen Umbenennung wird kein Teleport erfunden. Fehler beim Lesen des Zielrecords werden im Dashboard angezeigt. Bei einer entfernten/umbenannten Methode bleibt der Hook unbenutzt, sodass keine Versuche erscheinen. Nach einem Lumen-Update deshalb die nachstehenden Laufzeitprüfungen ausführen. Es gibt keine heuristische Erkennung beliebiger Positionssprünge als Teleportstart.

## Vanilla-Positionsadapter

`ServerPositionMixin` beobachtet `ClientPacketListener.handleMovePlayer` am `RETURN`, auf dem Clientthread und nur für dieselbe Verbindung. Dieses Pflichtziel wird von Minecraft 1.21.7 mit offiziellen Laufzeitnamen bereitgestellt; Forge 57 nutzt diese Namen ebenfalls. Der Mixin cancelt nichts, überschreibt keine Werte und bestätigt keine Pakete selbst. Die Position wird nach Vanillas Anwendung gelesen, deshalb sind relative XYZ-Flags bereits berücksichtigt.

Es wird jedes Positionspaket im aktiven Fenster protokolliert. Liegt dessen aufgelöstes Ergebnis außerhalb der Zieltoleranz, ist dies `CORRECTION`; am Ziel ist es nur `SERVER_POSITION`. Der Analyzer behauptet nicht, jede solche Position sei kausal durch genau diesen Teleport ausgelöst worden. Netzwerkempfang und Handlerausführung haben keine Teleport-Versuchs-ID des Lab.

## Lebenszyklus und Threading

Der Runtime-Adapter hält genau eine aktive Messung auf dem Clientthread. Ein weiterer angenommener Lumen-Versuch schließt die vorherige Beobachtung als `UNKNOWN/SUPERSEDED`, ohne Lumen zu blockieren. Neue Pakete werden nur der jetzt aktiven Messung zugeordnet. Sitzung/Verbindung und Dimension werden geprüft; Disconnect oder Tod unterbrechen die Messung. Ein vom Teleport erwarteter Einzelspielerwechsel von Ausgangs- zu Zieldimension ist erlaubt.

Client-Ticks liefern bei normalem Spiel etwa 20 Samples/s. Das Dashboard pausiert nicht. Echte Spielpausen liefern keine Samples; die monotone Uhr läuft weiter und ein großer Abstand verhindert `SUCCESS_STABLE`. Der Core verwendet niemals die Wandzeit zur Bestimmung einer Dauer. Ereignis-UTC = Start-UTC + monotone Dauer; tatsächliche UTC-Uhrkorrekturen innerhalb eines Versuchs verändern seine zeitliche Reihenfolge nicht.

Der Export kopiert abgeschlossene Snapshots auf dem Clientthread und schreibt sie anschließend auf genau einem Hintergrundthread. Jeder Export erhält einen neuen Ordner. Erst nach dem vollständigen Schreiben beider Dateien wird der `.partial`-Ordner veröffentlicht (atomare Umbenennung, wenn unterstützt). Fehler verändern die gespeicherten Messungen nicht; ein erneuter Export ist möglich. Ein abgebrochener Schreibvorgang kann zur Diagnose einen versteckten `.partial`-Ordner hinterlassen.

## Anpassung an andere Lumen-Versionen

Stabiler Vertrag für eine zukünftige direkte Schnittstelle:

```java
// Nach Annahme des existierenden Versuchs, Zeitstempel vorher am Eintritt erfassen:
analyzer.begin(origin, target, pseudonymousServerId, method, pingMs, startUtcMs, startMonoMs);
// Regelmäßig und nur beobachtend:
analyzer.sample(actualPosition, pingMs);
// Nach Vanilla-Anwendung:
analyzer.serverPosition(resolvedPosition);
// Bei Verlust der Beobachtbarkeit:
analyzer.interrupt(Reason.DISCONNECTED);
```

Sollte Lumen später selbst diese Schnittstelle anbieten, muss der optionale Start-Mixin entfernt werden, damit kein Doppelstart entsteht. Der bestehende Teleport-Algorithmus bleibt auch dann unverändert.

## Noch im realen Client prüfen

1. Lab ohne Lumen: Start ohne optionales Mixin-Ziel, F8 funktioniert, keine erfundenen Versuche.
2. Lab neben geprüftem Lumen: Aufzeichnung aus → keine Messung; Aufzeichnung an → genau eine Messung pro angenommenem F6/F5/Zurück-Aufruf.
3. Abgewiesenes Ziel / Cooldown → kein Versuch; Ursprungsposition muss vor Lumens lokalem Umsetzen liegen.
4. Einzelspieler-Ankunft einschließlich Dimensionswechsel beobachten; Match-Position darf keine Korrektur ergeben.
5. In einer autorisierten Testumgebung tatsächliche relative Serverpositionspakete und spätere Rücksetzungen beobachten; CSV mit Laufzeitlogs vergleichen.
6. Stoppen, Disconnect, F8-Details, kleine GUI-Skalierungen, Export und Neustart prüfen.

Diese Schritte benötigen einen laufenden Minecraft-Client; kompilierte Quellen und Simulationen ersetzen sie nicht. Der aktuelle Prüfstand ist in [VERIFICATION.md](VERIFICATION.md) festgehalten.
