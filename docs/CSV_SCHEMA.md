# CSV-Schema v1

Zwei UTF-8-Dateien ohne BOM, Komma als Trenner, CRLF-Zeilenenden, doppelte Anführungszeichen und deren Verdopplung nach üblichen CSV-Regeln. Zahlen verwenden Dezimalpunkt ohne Tausendertrenner. Leeres Feld = unbekannt/nicht anwendbar. Boolesche Werte: `true`/`false`. Zeitstempel im ISO-8601-Format `YYYY-MM-DDTHH:mm:ss.SSSZ`; dazu Epoch-Millisekunden als Ganzzahl. Dauerwerte sind Millisekunden seit Beginn dieses Versuchs. Kein Upload.

## teleport_attempts.csv

Genau eine Zeile je **abgeschlossenem, noch gespeichertem** Versuch des Exports. Spaltenreihenfolge ist die folgende:

| Feld | Typ / Einheit | Bedeutung |
| --- | --- | --- |
| schema_version | Text | `1` |
| attempt_id | UUID-Text | Zufällige ID eines Versuchs, keine Spieler-UUID |
| started_utc | UTC-Text | Erfasste Uhrzeit am Lumen-Methodeneintritt |
| started_utc_ms | int / ms seit Unix-Epoche | Derselbe Start |
| server_id | Text | `srv_` + 24 HMAC-Hexzeichen; pseudonym, installierungsabhängig |
| method | Enum | `CLIENT_ONLY` oder `SINGLEPLAYER` |
| origin_dimension | Text | Minecraft-Dimensionskennung am Anfang |
| target_dimension | Text | Dimensionskennung des Zielrecords |
| origin_x | double / Blöcke | Ursprung X |
| origin_y | double / Blöcke | Ursprung Y |
| origin_z | double / Blöcke | Ursprung Z |
| target_x | double / Blöcke | Ziel X |
| target_y | double / Blöcke | Ziel Y |
| target_z | double / Blöcke | Ziel Z |
| horizontal_distance_blocks | double / Blöcke | `hypot(dx,dz)`; leer bei Dimensionswechsel |
| vertical_delta_blocks | double / Blöcke | `Ziel-Y − Ursprung-Y`, mit Vorzeichen; leer bei Dimensionswechsel |
| vertical_distance_blocks | double / Blöcke | Betrag des vertikalen Deltas; leer bei Dimensionswechsel |
| total_distance_blocks | double / Blöcke | Euklidische 3D-Distanz; leer bei Dimensionswechsel |
| observation_window_ms | int / ms | Konfiguriertes Sollfenster |
| observed_duration_ms | int / ms | Zeit bis Abschluss/Abbruch; bei später Erkennung einer Lücke auch länger als Sollfenster |
| checkpoint_schedule_ms | Text / ms | Geplante Offsets, semikolongetrennt: `1000;3000;5000;10000` |
| target_tolerance_blocks | double / Blöcke | Radius für Zielankunft |
| stationary_tolerance_blocks | double / Blöcke | Radius um den letzten stationären Clusteranker |
| minimum_stable_ms | int / ms | Mindestdauer für stabile Endposition |
| maximum_sample_gap_ms | int / ms | Maximal akzeptierter Sampleabstand / Kontrollpunktverspätung |
| initial_ping_ms | int / ms, optional | Vorhandener Ping bei Veröffentlichung des angenommenen Versuchs |
| average_ping_ms | double / ms, optional | Mittel der verfügbaren Sample-Pings |
| first_local_arrival_ms | int / ms, optional | Erster lokaler Sample innerhalb Zieltoleranz und Dimension |
| first_server_response_ms | int / ms, optional | Erste angewendete Serverposition im Fenster; keine pauschale Paket-RTT |
| stable_end_elapsed_ms | int / ms, optional | Beginn des letzten hinreichend langen stationären Positionsclusters |
| end_dimension | Text, optional | Dimension des letzten gültigen lokalen Samples |
| end_x | double / Blöcke, optional | Letztes gültiges X |
| end_y | double / Blöcke, optional | Letztes gültiges Y |
| end_z | double / Blöcke, optional | Letztes gültiges Z |
| final_deviation_blocks | double / Blöcke, optional | End-Ziel-Distanz; leer bei Dimensionsunterschied |
| server_target_observed | bool | Mindestens eine explizite Serverposition im Zielbereich beobachtet |
| correction_count | int | Anzahl von Serverpositionen außerhalb des Zielbereichs |
| sample_gap | bool | Zeitlücke über dem konfigurierten Grenzwert festgestellt |
| status | Enum | `SUCCESS_STABLE`, `CORRECTED`, `PARTIAL`, `TIMEOUT`, `UNKNOWN` |
| reason | Enum | Beobachtbarer Grund; siehe unten |

## teleport_events.csv

Über alle enthaltenen Versuche nach `event_utc_ms`, dann `attempt_id` und `sequence` sortiert. Innerhalb eines Versuchs ist `sequence` die eindeutige Reihenfolge auch bei identischen Millisekunden. Nach Uhrkorrekturen zwischen Versuchen ist UTC nur so genau wie die lokale Uhr; `elapsed_ms` ist monoton.

| Feld | Typ / Einheit | Bedeutung |
| --- | --- | --- |
| schema_version | Text | `1` |
| attempt_id | UUID-Text | Fremdschlüssel auf Versuchszeile |
| sequence | int | Nullbasierter Ereigniszähler innerhalb des Versuchs |
| event_utc | UTC-Text | Start-UTC plus monotone Dauer |
| event_utc_ms | int / Epoch-ms | Dieselbe Ereigniszeit |
| elapsed_ms | int / ms | Tatsächliche Zeit seit Methodeneintritt |
| event_type | Enum | Siehe Ereignisse |
| dimension | Text, optional | Dimension der beobachteten Position |
| x | double / Blöcke, optional | Beobachtetes X |
| y | double / Blöcke, optional | Beobachtetes Y |
| z | double / Blöcke, optional | Beobachtetes Z |
| ping_ms | int / ms, optional | Ping dieses Samples, wenn verfügbar |
| scheduled_checkpoint_ms | int / ms, optional | Geplanter Offset nur bei `CHECKPOINT`; Messzeit steht in `elapsed_ms` |

### Ereignisse

- `START`: Ursprung; angenommener Lumen-Versuch.
- `LOCAL_POSITION`: lokale Veränderung gegenüber vorherigem Sample um mehr als 0,0001 Blöcke oder Dimensionswechsel.
- `SAMPLE`: Tick-Sample, auch bei unveränderter Position, zur unabhängigen Abdeckungsprüfung.
- `CHECKPOINT`: tatsächlicher Sample beim/nach dem geplanten Offset. Bei einer großen Lücke können mehrere Offsets denselben tatsächlichen Sample haben; das ist keine historische Rekonstruktion.
- `SERVER_POSITION`: bereits von Vanilla aufgelöste und angewendete Serverposition.
- `CORRECTION`: dieselbe Serverposition zusätzlich als außerhalb des Zielbereichs markiert. **Nicht doppelt als zwei Pakete zählen.**
- `FINISH`: Abschluss mit zuletzt gültiger Sampleposition. Ergebnis und Grund stehen in der Versuchszeile.

### Gründe

| Grund | Beobachtung |
| --- | --- |
| SERVER_TARGET_AND_CONTINUOUS_SAMPLES | Serverbeleg am Ziel plus vollständige stabile Beobachtung |
| SERVER_POSITION_OFF_TARGET | Serverpositionspaket außerhalb des Zielbereichs |
| MOVED_WITHOUT_STABLE_TARGET | Lokale Bewegung ohne vollständige stabile Zielbeobachtung |
| LOCAL_TARGET_WITHOUT_SERVER_EVIDENCE | Lokal am Ziel, kein expliziter Serverpositionsbeleg |
| NO_CLEAR_RESULT | Weder deutliche Bewegung noch ausreichender Erfolgsbeleg |
| DISCONNECTED | Verbindung verloren |
| SESSION_CHANGED | Neue Clientverbindung |
| DIMENSION_CHANGED | Unerwartete Dimension |
| PLAYER_UNAVAILABLE | Spieler/Welt nicht beobachtbar oder Spieler tot |
| RECORDING_STOPPED | Aufzeichnung manuell beendet |
| SUPERSEDED | Nächster angenommener Teleport beendet bisherige Zuordnung |
| SAMPLE_GAP | Zu großer zeitlicher Abstand / zu spätes Fensterende |
| MISSING_CHECKPOINTS | Positionspaket nach Fensterende ohne vollständigen Endsample |
| OBSERVATION_INTERRUPTED | Für alternative direkte Adapter reservierter allgemeiner Abbruch |

`reason` beschreibt Beobachtungen, keine serverinternen Entscheidungen. Alle Ergebnisregeln stehen in README und im Java-Kern. Für neue Semantik oder Struktur ist die Schema-Version zu erhöhen; keine stillen Spaltenumbenennungen.
