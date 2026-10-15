package dev.lumen.teleportlab.core;

import java.io.StringWriter;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Deterministic Java 21 regression suite, no game or network required. */
public final class SimulationTest {
    private static final Position ORIGIN = new Position("minecraft:overworld", 0, 64, 0);
    private static final Position TARGET = new Position("minecraft:overworld", 100, 70, 40);
    private static final String SERVER = new ServerPseudonym(new byte[32]).identify("example.invalid:25565");
    private static int passed;
    private static final List<AttemptSnapshot> examples = new ArrayList<>();
    private static final class Clock implements LabClock {
        long mono, wall = 1792080000000L;
        public long utcMillis() { return wall + mono; }
        public long monotonicMillis() { return mono; }
    }
    private static final class Fixture {
        final Clock clock = new Clock();
        final TeleportAnalyzer lab;
        Fixture() { this(LabConfig.defaults()); }
        Fixture(LabConfig config) { lab = new TeleportAnalyzer(config, clock); lab.setRecording(true); }
        void begin() { lab.begin(ORIGIN, TARGET, SERVER, "CLIENT_ONLY", 42); }
        void at(long ms, Position p) { clock.mono = ms; lab.sample(p, 42); }
        void run(long from, long until, Position p) { for (long t = from; t <= until; t += 50) at(t, p); }
        AttemptSnapshot end() { return lab.snapshots().getLast(); }
    }
    public static void main(String[] args) throws Exception {
        test("disabled by default", f -> {
            var lab = new TeleportAnalyzer(LabConfig.defaults(), f.clock);
            check(lab.begin(ORIGIN, TARGET, SERVER, "CLIENT_ONLY", 10).isEmpty(), "recording must be opt-in");
            check(lab.snapshots().isEmpty(), "no disabled data");
        });
        test("stable target with server evidence", f -> {
            f.begin(); f.run(0, 100, TARGET); f.lab.serverPosition(TARGET); f.run(150, 10000, TARGET);
            expect(f, Status.SUCCESS_STABLE); check(f.end().firstServerResponseMs() == 100, "reaction timing");
            check(f.end().firstLocalArrivalMs() == 0, "local arrival");
            check(f.end().events().stream().filter(e -> e.type() == LabEvent.Type.CHECKPOINT).count() == 4, "four checkpoints");
            examples.add(f.end());
        });
        test("local target is not success", f -> {
            f.begin(); f.run(0, 10000, TARGET); expect(f, Status.TIMEOUT);
            check(f.end().reason() == Reason.LOCAL_TARGET_WITHOUT_SERVER_EVIDENCE, "explicit uncertainty"); examples.add(f.end());
        });
        test("late rollback at six seconds", f -> {
            f.begin(); f.at(0, TARGET); f.lab.serverPosition(TARGET); f.run(50, 5950, TARGET);
            f.clock.mono = 6000; f.lab.serverPosition(ORIGIN); f.run(6000, 10000, ORIGIN);
            expect(f, Status.CORRECTED); check(f.end().corrections() == 1, "one correction"); examples.add(f.end());
        });
        test("delayed correction at 9.5 seconds", f -> {
            f.begin(); f.run(0, 9450, TARGET); f.clock.mono = 9500; f.lab.serverPosition(ORIGIN);
            f.run(9500, 10000, ORIGIN); expect(f, Status.CORRECTED);
            check(f.end().firstServerResponseMs() == 9500, "delay recorded"); examples.add(f.end());
        });
        test("partial movement", f -> {
            f.begin(); f.run(0, 10000, new Position("minecraft:overworld", 30, 64, 0));
            expect(f, Status.PARTIAL); examples.add(f.end());
        });
        test("no movement times out", f -> { f.begin(); f.run(0, 10000, ORIGIN); expect(f, Status.TIMEOUT); });
        test("disconnect before window ends", f -> {
            f.begin(); f.run(0, 750, TARGET); f.clock.mono = 800; f.lab.interrupt(Reason.DISCONNECTED);
            expect(f, Status.UNKNOWN); check(f.end().reason() == Reason.DISCONNECTED, "disconnect reason"); examples.add(f.end());
        });
        test("next attempt supersedes only attribution window", f -> {
            f.begin(); f.run(0, 950, TARGET); f.clock.mono = 1000; f.begin();
            check(f.lab.snapshots().getFirst().reason() == Reason.SUPERSEDED, "old attempt closed");
            f.lab.serverPosition(ORIGIN); f.run(1000, 11000, ORIGIN); expect(f, Status.CORRECTED);
            check(f.lab.snapshots().getFirst().corrections() == 0, "packet belongs only to new attempt");
        });
        test("sequential completed attempts have unique IDs", f -> {
            f.begin(); f.run(0, 10000, TARGET); f.clock.mono = 11000; f.begin(); f.run(11000, 21000, TARGET);
            check(f.lab.snapshots().size() == 2, "two independent attempts");
            check(!f.lab.snapshots().getFirst().id().equals(f.end().id()), "unique IDs");
        });
        test("observation gaps invalidate stability", f -> {
            f.begin(); f.at(0, TARGET); f.lab.serverPosition(TARGET); f.run(50, 500, TARGET); f.run(2000, 10000, TARGET);
            expect(f, Status.UNKNOWN); check(f.end().sampleGap(), "gap recorded"); examples.add(f.end());
        });
        test("late final tick does not invent endpoint", f -> {
            f.begin(); f.at(0, TARGET); f.lab.serverPosition(TARGET); f.run(50, 9950, TARGET);
            f.at(12000, ORIGIN); expect(f, Status.UNKNOWN); check(f.end().end().equals(TARGET), "last valid endpoint retained");
        });
        test("leaving and returning is not held target", f -> {
            f.begin(); f.at(0, TARGET); f.lab.serverPosition(TARGET); f.run(50, 2000, TARGET);
            f.run(2050, 3000, ORIGIN); f.run(3050, 10000, TARGET); expect(f, Status.PARTIAL);
        });
        test("late arrival misses early checkpoints", f -> {
            f.begin(); f.run(0, 3450, ORIGIN); f.clock.mono = 3500; f.lab.serverPosition(TARGET);
            f.run(3500, 10000, TARGET); expect(f, Status.PARTIAL);
        });
        test("matching server position is not correction", f -> {
            f.begin(); f.at(0, TARGET); f.lab.serverPosition(TARGET); f.run(50, 10000, TARGET);
            expect(f, Status.SUCCESS_STABLE); check(f.end().corrections() == 0, "matching packet");
        });
        test("stopping marks active attempt unknown", f -> {
            f.begin(); f.at(0, TARGET); f.lab.setRecording(false); expect(f, Status.UNKNOWN);
            check(f.end().reason() == Reason.RECORDING_STOPPED, "stop reason");
        });
        test("session switch cannot produce success", f -> {
            f.begin(); f.at(0, TARGET); f.lab.serverPosition(TARGET); f.lab.interrupt(Reason.SESSION_CHANGED); expect(f, Status.UNKNOWN);
        });
        test("dimension mismatch is not arrival", f -> {
            f.begin(); f.run(0, 10000, new Position("minecraft:the_nether", 100, 70, 40));
            check(f.end().firstLocalArrivalMs() == null, "dimensions included"); check(f.end().finalDeviation() == null, "no cross-dimension deviation");
        });
        test("integrated dimension transition is observable", f -> {
            Position nether = new Position("minecraft:the_nether", 10, 60, 10);
            f.lab.begin(ORIGIN, nether, SERVER, "SINGLEPLAYER", null); f.at(0, nether); f.lab.serverPosition(nether); f.run(50, 10000, nether);
            expect(f, Status.SUCCESS_STABLE); check(!f.end().comparableDimensions(), "cross dimension distance undefined"); examples.add(f.end());
        });
        test("monotonic duration survives UTC clock jump", f -> {
            f.begin(); f.at(0, TARGET); f.clock.wall -= 3600000; f.lab.serverPosition(TARGET); f.run(50, 10000, TARGET);
            expect(f, Status.SUCCESS_STABLE); check(f.end().observedMs() == 10000, "monotonic window");
            check(f.end().events().getLast().utcMs() - f.end().startedUtcMs() == 10000, "anchored event UTC");
        });
        test("reset retains opt-in but clears history", f -> {
            f.begin(); f.at(0, TARGET); f.lab.reset(); check(f.lab.snapshots().isEmpty() && f.lab.recording(), "reset");
        });
        test("invalid config rejected", f -> {
            boolean rejected = false;
            try { new LabConfig(10000, List.of(1000L), 1.5, .1, 1000, 500, 500); }
            catch (IllegalArgumentException e) { rejected = true; }
            check(rejected, "window checkpoint required");
        });
        test("method entry timestamp retained after acceptance", f -> {
            long utc = f.clock.utcMillis(); f.clock.mono = 25;
            f.lab.begin(ORIGIN, TARGET, SERVER, "CLIENT_ONLY", 42, utc, 0); f.at(25, TARGET);
            f.lab.interrupt(Reason.RECORDING_STOPPED);
            check(f.end().firstLocalArrivalMs() == 25 && f.end().startedUtcMs() == utc, "entry-to-arrival timing");
        });
        test("post-window packet cannot manufacture missing samples", f -> {
            f.begin(); f.at(0, TARGET); f.lab.serverPosition(TARGET); f.run(50, 9950, TARGET);
            f.clock.mono = 10001; f.lab.serverPosition(TARGET); expect(f, Status.UNKNOWN);
            check(f.end().reason() == Reason.MISSING_CHECKPOINTS, "actual final sample required");
        });
        test("unknown ping is absent", f -> {
            f.lab.begin(ORIGIN, TARGET, SERVER, "CLIENT_ONLY", -1);
            for (int t = 0; t <= 10000; t += 50) { f.clock.mono = t; f.lab.sample(TARGET, null); }
            check(f.end().initialPingMs() == null && f.end().averagePingMs() == null, "no invented zero ping");
        });
        test("server identity is keyed and normalized", f -> {
            byte[] otherKey = new byte[32]; otherKey[0] = 1;
            var a = new ServerPseudonym(new byte[32]); var b = new ServerPseudonym(otherKey);
            check(a.identify(" Example.INVALID:25565 ").equals(SERVER), "canonical identity");
            check(!b.identify("example.invalid:25565").equals(SERVER), "different secret");
        });
        test("bounded history counts eviction", f -> {
            var c = LabConfig.defaults(); var tiny = new LabConfig(c.observationMs(), c.checkpointsMs(), 1.5, .1, 1000, 500, 1);
            var lab = new TeleportAnalyzer(tiny, f.clock); lab.setRecording(true);
            lab.begin(ORIGIN, TARGET, SERVER, "CLIENT_ONLY", null); lab.interrupt(Reason.DISCONNECTED);
            lab.begin(ORIGIN, TARGET, SERVER, "CLIENT_ONLY", null); lab.interrupt(Reason.DISCONNECTED);
            check(lab.snapshots().size() == 1 && lab.evictedAttempts() == 1, "history bound");
        });
        test("success denominator includes unclear outcomes", f -> {
            var stats = DashboardStats.of(examples);
            check(stats.stable() == 2 && stats.unclear() == 3, "unclear count");
            check(Math.abs(stats.successRate() - 200.0 / examples.size()) < .001, "all completed denominator");
        });
        StringWriter quoted = new StringWriter(); CsvExporter.row(quoted, "Unicode ä", "a,b", "say \"hi\"", "a\nb", null, 1.25);
        check(quoted.toString().equals("Unicode ä,\"a,b\",\"say \"\"hi\"\"\",\"a\nb\",,1.25\r\n"), "RFC CSV escaping"); passed++;
        Path exported = new CsvExporter().export(Path.of("core/build/example-exports"), examples);
        Files.createDirectories(Path.of("examples"));
        for (String file : List.of("teleport_attempts.csv", "teleport_events.csv"))
            Files.copy(exported.resolve(file), Path.of("examples", file), StandardCopyOption.REPLACE_EXISTING);
        check(!Files.readString(exported.resolve("teleport_attempts.csv")).contains("example.invalid"), "no raw server address"); passed++;
        System.out.println("PASS: " + passed + " simulation / CSV checks; " + examples.size() + " synthetic attempts exported to examples/");
    }
    private static void test(String name, Consumer<Fixture> test) {
        try { test.accept(new Fixture()); passed++; System.out.println("PASS " + name); }
        catch (Throwable failure) { throw new AssertionError("FAIL " + name, failure); }
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void expect(Fixture f, Status status) { check(f.end().status() == status, "expected " + status + ", got " + f.end().status()); }
}
