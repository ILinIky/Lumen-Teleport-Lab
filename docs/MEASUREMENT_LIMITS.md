# Was die Messungen aussagen

| Messwert | Quelle und Aussage |
| --- | --- |
| Ursprung/Ziel | Lumen-Methodeneintritt und bestehender Zielrecord, ohne Namen |
| Lokale Position | Tatsächlich im Client-Tick gelesene Spielerposition; kann dem Server vorauseilen |
| Erste lokale Ankunft | Erster Sample im richtigen Zielbereich/Dimension; Genauigkeit durch Tickrate und Methodenausführungsdauer begrenzt |
| Ping | Vorhandener `PlayerInfo.getLatency()`-Wert; vom Spiel aktualisiert, keine aktive Messung des Lab |
| Mittlerer Ping | Arithmetisches Mittel der verfügbaren Tickwerte, keine paketgewichtete oder exakte RTT-Analyse |
| Erste Serverreaktion | Zeit bis zur ersten nach Vanilla-Anwendung beobachteten Serverposition derselben Verbindung im Messfenster |
| Korrekturen | Aufgelöste Serverposition außerhalb der Zieltoleranz; kein Grundcode des Servers |
| Stabile Endposition | Beginn des letzten stationären Clusters: Samples bleiben innerhalb `stationary_tolerance_blocks` des Clusterankers für mindestens `stable_ms` |
| Endabweichung | Euklidische Distanz des letzten gültigen lokalen Samples zum Ziel; bei unterschiedlicher Dimension leer |
| Stabile Zielposition | Explizite Serverposition am Ziel plus kontinuierliche Clientbeobachtung im Fenster; keine unbegrenzte Garantie |

Ein Server sendet typischerweise keine Bestätigung jeder normalen Clientbewegung. Fehlendes Positionspaket, normale Spielpakete, geladene Chunks oder ein verstrichener Timeout beweisen deshalb keine Teleportannahme. Das Lab bleibt in diesem Fall bewusst konservativ.

Die Serverreaktionszeit enthält Client-/Serverplanung, Pakettransport, Warteschlangen und Handlerverzögerung. Sie ist **keine isolierte Netzwerklatenz**, keine Tickzeit des Servers und keine Messung eines Anticheat-Entscheidungswegs. Das Lab instrumentiert weder den Socketeingang noch Paketversand oder Servercode. Ping und Reaktionszeit dürfen statistisch verglichen, aber nicht als identische Größen interpretiert werden.

Eine explizite Serverposition kann verspätet sein oder einen anderen Anlass haben. Ein bereits vor dem Versuch gesendetes Paket kann im Fenster verarbeitet werden. Bei aufeinanderfolgenden Versuchen können verspätete Pakete des vorherigen Versuchs nicht sicher kausal zugeordnet werden. Nur die **zeitliche Zuordnung zur aktiven Beobachtung** ist bekannt. `SUPERSEDED` macht das Ende der vorherigen Zuordnung sichtbar.

Natürliches Gehen, Fallen, Portale, Respawn oder andere Mods können Positionen ändern. Positionsveränderung ohne Serverpositionspaket wird nicht einfach als Serverkorrektur bezeichnet. Ein sichtbarer Rücksprung allein kann `PARTIAL` sein. Wer das Ziel nach Ankunft selbst verlässt, erfüllt ebenfalls nicht „Ziel gehalten“. Während Forschungsversuchen möglichst kontrollierte Bedingungen verwenden.

Nach Ablauf des Fensters werden spätere Rücksetzungen nicht mehr diesem Versuch zugerechnet. Für die Fragestellung geeignete Fenster wählen und alle Konfigurationsfelder mit auswerten. `observed_duration_ms` darf wegen des ersten passenden Endticks geringfügig über dem Sollfenster liegen; Überschreitung bis maximal `max_sample_gap_ms` ist als Messauflösung zulässig. Größere Überschreitungen, Pausen und Hänger werden unklar. Kontrollpunkte protokollieren immer tatsächliche Zeit plus geplanten Offset, keine erfundenen historischen Positionen.

`stable_end_elapsed_ms` ist der Beginn des letzten im Nachhinein mindestens `stable_ms` stabil gebliebenen Clusters. Die Stabilität war zu diesem Zeitpunkt noch nicht bekannt. Dieser Wert kann auch bei `CORRECTED` oder `TIMEOUT` vorkommen, etwa bei einer stabilen Rücksetzungsposition; er ist kein Erfolgssignal für sich.

Statistische Grenzen: Nur angenommene, bei eingeschalteter Aufzeichnung gestartete Lumen-Aufrufe sind in der Stichprobe. Abgewiesene Aufrufe sind nicht enthalten. Retention verwirft ältere Versuche; Exporte können sich überschneiden. `UNKNOWN`, `TIMEOUT`, fehlende Pings, Dimensionswechsel und konfigurierte Toleranzen müssen in jeder Auswertung sichtbar bleiben. Korrelation zwischen Distanz/Ping und Ergebnis belegt keine interne Serverregel oder Kausalität.
