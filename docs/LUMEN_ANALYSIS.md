# Analyse der vorhandenen Teleport-Implementierung

Quelle: [ILinIky/Minecraft-Lumen-Client](https://github.com/ILinIky/Minecraft-Lumen-Client), `main` am 15.10.2026, geprüfter Commit **4e6442fbc3cb126534491d42eb28eda86a850060**. Über den GitHub-Connector wurden die tatsächlichen Java-Dateien gelesen. Der lokale `mc_2026`-Quellstand wurde zusätzlich als Referenz geprüft. Der Analyzer verändert diesen Bestand nicht.

## Einstieg und Wegpunkte

[`TeleportService.java`](https://github.com/ILinIky/Minecraft-Lumen-Client/blob/4e6442fbc3cb126534491d42eb28eda86a850060/src/main/java/com/example/examplemod/waypoint/TeleportService.java) enthält den gemeinsamen Einstieg `teleportTo(Location, String)`.

- `teleport(Waypoint)` übernimmt Dimension, X/Y/Z, Yaw und Pitch aus dem gespeicherten Wegpunkt. Ein erfolgreich angenommener Aufruf merkt das letzte Ziel.
- `teleportLast()` sucht den gespeicherten Wegpunkt nach ID erneut und verwendet ersatzweise das vorherige Objekt.
- `teleportBack()` verwendet die vor einem bestätigten oder unbestätigten Versuch gemerkte Ausgangsposition.
- F6 ist das Wegpunktmenü, F5 der letzte Teleport; F7 öffnet die Speicherung eines Wegpunkts. Diese Oberflächen lösen die vorhandenen Service-Aufrufe aus, keinen gesonderten Teleportweg.

Vor einem Versuch prüft Lumen Spieler/Welt, Tod, laufenden Versuch, Mindestabstand einer Sekunde, gültige endliche Zielwerte, Dimension, Fahrzeuge und Entfernung zum bereits erreichten Ziel. Abgewiesene Aufrufe geben `false` zurück und werden vom Lab nicht als gestartete Versuche gezählt.

## Fremder Server

`Method.CLIENT_ONLY` ist nur innerhalb derselben Dimension verfügbar. Lumen setzt **einmal** lokal Position und Blickrichtung, setzt die Bewegung auf null und die Fallstrecke zurück. Es gibt keinen eigenen Teleport-Netzwerkkanal, kein `/tp`, keine wiederholten Zielsendungen und keinen Haltemechanismus. Normale Bewegungspakete kommen aus Vanillas regulärer Clientlogik. Der Lab-Analyzer sendet und verändert überhaupt keine Pakete.

Die lokale Zielkoordinate ist deshalb zunächst eine Clientbeobachtung. Aus diesem lokalen Umsetzen allein lässt sich keine serverseitige Annahme ableiten. Ein normaler Server muss nicht jede akzeptierte Bewegung mit einem expliziten Positionspaket beantworten.

## Integrierter Einzelspielerserver

`teleportOnIntegratedServer()` plant die vorhandene Aktion auf dem Serverthread ein. Dort wird der Server-Spieler in der angegebenen Dimension über `ServerPlayer.teleportTo(...)` umgesetzt. Die Rückgabe wird zurück zum Clientthread übermittelt und dort in Lumens bisherigen Fortschritt eingespeist. Vor Ausführung setzt der Client seine Position nicht eigenständig fest. Der Lab-Analyzer berührt weder diesen Auftrag noch seinen Rückgabewert und beobachtet weiterhin die Clientposition sowie Positionspakete.

## Serverantwort und Lumens bisherige Bewertung

[`ClientPacketListenerMixin.java`](https://github.com/ILinIky/Minecraft-Lumen-Client/blob/4e6442fbc3cb126534491d42eb28eda86a850060/src/mc/1.21.7/java/com/example/examplemod/mixin/ClientPacketListenerMixin.java) ruft nach Vanillas `handleMovePlayer` den Service `onServerPosition` auf. Vanilla hat zu diesem Zeitpunkt relative Positionsflags aufgelöst, die Position angewendet und die notwendige Bestätigung selbst übernommen. Der bestehende Service vergleicht die aktuelle Spielerposition mit dem Ziel, Toleranz **1,5 Blöcke**, einschließlich Dimension.

[`TeleportAttempt.java`](https://github.com/ILinIky/Minecraft-Lumen-Client/blob/4e6442fbc3cb126534491d42eb28eda86a850060/src/main/java/com/example/examplemod/waypoint/TeleportAttempt.java) unterscheidet:

| Bisheriger Lumen-Status | Bedingung |
| --- | --- |
| `WAITING` | Versuch läuft |
| `CONFIRMED` | Serverbeleg und lokale Ankunft mit geladenen Zielchunks |
| `UNCONFIRMED` | Fremdserver, mindestens 100 Client-Ticks und zuvor mindestens fünf stabile Ticks am lokal geladenen Ziel, ohne Serverbestätigung |
| `REJECTED` | Beobachtete abweichende Serverposition |
| `TIMED_OUT` | Nach 200 Ticks keine passendere Abschlussbedingung |
| `CANCELLED` | Abbruch durch Spieler-/Sitzungs-/Weltwechsel oder andere bestehende Abbruchbedingungen |

Lumens laufender Versuch kann früh enden. Danach beobachtet sein `onServerPosition` diesen Versuch nicht weiter. Ein Beispiel: `CONFIRMED` nach kurzer Ankunft, Rücksetzung sechs Sekunden später. Genau diese Lücke schließt das Lab mit einem **eigenen längeren Beobachtungsfenster**, ohne Lumens Service oder bisherigen Status zu verändern.

Chunks allein sind in Lumen bereits kein Serverbeleg. Auch das Lab behandelt geladene Chunks nicht als Bestätigung. Die Zeitfenster des Lab laufen in monotonen Millisekunden und sind unabhängig von Lumens tickbasierten Grenzen.

## Konsequenz für die Integration

Eine eigenständige Forge-Mod ist möglich: ein optionaler Mixin in Lumens gemeinsamen Einstieg plus ein Vanilla-Positionsbeobachter und ein Client-Tick-Sampler genügen. Keine Abhängigkeit gegen die Lumen-JAR beim Kompilieren, keine Quelländerung und keine Umstellung des Teleports. Technische Grenzen dieses versionsgebundenen Hooks stehen in [INTEGRATION.md](INTEGRATION.md).
