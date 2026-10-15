package dev.lumen.teleportlab.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.*;

/** New directory per snapshot; consumers never see a half-written CSV pair. */
public final class CsvExporter {
    private static final DateTimeFormatter UTC = new DateTimeFormatterBuilder().appendInstant(3).toFormatter();
    public static final String VERSION = "1";
    public static final List<String> ATTEMPT_COLUMNS = List.of(
            "schema_version", "attempt_id", "started_utc", "started_utc_ms", "server_id", "method",
            "origin_dimension", "target_dimension", "origin_x", "origin_y", "origin_z", "target_x", "target_y", "target_z",
            "horizontal_distance_blocks", "vertical_delta_blocks", "vertical_distance_blocks", "total_distance_blocks",
            "observation_window_ms", "observed_duration_ms", "checkpoint_schedule_ms", "target_tolerance_blocks",
            "stationary_tolerance_blocks", "minimum_stable_ms", "maximum_sample_gap_ms", "initial_ping_ms", "average_ping_ms",
            "first_local_arrival_ms", "first_server_response_ms", "stable_end_elapsed_ms", "end_dimension", "end_x", "end_y", "end_z",
            "final_deviation_blocks", "server_target_observed", "correction_count", "sample_gap", "status", "reason");
    public static final List<String> EVENT_COLUMNS = List.of(
            "schema_version", "attempt_id", "sequence", "event_utc", "event_utc_ms", "elapsed_ms", "event_type",
            "dimension", "x", "y", "z", "ping_ms", "scheduled_checkpoint_ms");

    public Path export(Path root, List<AttemptSnapshot> snapshot) throws IOException {
        List<AttemptSnapshot> attempts = snapshot.stream().filter(AttemptSnapshot::complete).toList();
        Files.createDirectories(root);
        String name = "teleport-" + Instant.now().toEpochMilli() + "-" + UUID.randomUUID();
        Path partial = root.resolve("." + name + ".partial");
        Path destination = root.resolve(name);
        Files.createDirectory(partial);
        try {
            try (var out = writer(partial.resolve("teleport_attempts.csv"))) {
                row(out, ATTEMPT_COLUMNS.toArray());
                for (var a : attempts) {
                    boolean same = a.comparableDimensions();
                    Position end = a.end();
                    row(out, VERSION, a.id(), utc(a.startedUtcMs()), a.startedUtcMs(), a.serverId(), a.method(),
                            a.origin().dimension(), a.target().dimension(), a.origin().x(), a.origin().y(), a.origin().z(),
                            a.target().x(), a.target().y(), a.target().z(),
                            same ? a.origin().horizontalTo(a.target()) : null,
                            same ? a.target().y() - a.origin().y() : null,
                            same ? Math.abs(a.target().y() - a.origin().y()) : null,
                            same ? a.origin().distanceTo(a.target()) : null,
                            a.config().observationMs(), a.observedMs(),
                            String.join(";", a.config().checkpointsMs().stream().map(Object::toString).toList()),
                            a.config().targetTolerance(), a.config().stationaryTolerance(), a.config().stableMs(),
                            a.config().maxSampleGapMs(), a.initialPingMs(), a.averagePingMs(), a.firstLocalArrivalMs(),
                            a.firstServerResponseMs(), a.stableEndMs(), end == null ? null : end.dimension(),
                            end == null ? null : end.x(), end == null ? null : end.y(), end == null ? null : end.z(),
                            a.finalDeviation(), a.serverTargetObserved(), a.corrections(), a.sampleGap(), a.status(), a.reason());
                }
            }
            try (var out = writer(partial.resolve("teleport_events.csv"))) {
                row(out, EVENT_COLUMNS.toArray());
                // UTC is anchored at START + monotonic duration, so wall-clock adjustments cannot reorder a run.
                var events = attempts.stream().flatMap(a -> a.events().stream())
                        .sorted(Comparator.comparingLong(LabEvent::utcMs).thenComparing(e -> e.attemptId().toString())
                                .thenComparingInt(LabEvent::sequence)).toList();
                for (var e : events) {
                    Position p = e.position();
                    row(out, VERSION, e.attemptId(), e.sequence(), utc(e.utcMs()), e.utcMs(), e.elapsedMs(), e.type(),
                            p == null ? null : p.dimension(), p == null ? null : p.x(), p == null ? null : p.y(),
                            p == null ? null : p.z(), e.pingMs(), e.checkpointMs());
                }
            }
            try { Files.move(partial, destination, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(partial, destination); }
            return destination;
        } catch (IOException failure) {
            // Leave .partial for manual recovery; never present it as a completed export.
            throw failure;
        }
    }
    private static BufferedWriter writer(Path path) throws IOException {
        return Files.newBufferedWriter(path, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    }
    private static String utc(long ms) { return UTC.format(Instant.ofEpochMilli(ms)); }
    static void row(Writer out, Object... values) throws IOException {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) out.write(',');
            String s = values[i] == null ? "" : values[i].toString();
            if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0)
                out.write('"' + s.replace("\"", "\"\"") + '"');
            else out.write(s);
        }
        out.write("\r\n");
    }
}
