package dev.lumen.teleportlab.core;

import java.util.*;

/** Pure observation state machine. Call on one client thread; snapshots are immutable. */
public final class TeleportAnalyzer {
    private final LabConfig config;
    private final LabClock clock;
    private final ArrayList<AttemptSnapshot> history = new ArrayList<>();
    private Run active;
    private boolean recording;
    private long evicted;

    private static final class Run {
        final UUID id = UUID.randomUUID();
        final String server, method;
        final long utc, start;
        final Position origin, target;
        final Integer ping;
        final ArrayList<LabEvent> events = new ArrayList<>();
        Position end, anchor;
        long lastSample, stableSince, pingSum, pingCount;
        Long arrival, response;
        int checkpoint, corrections;
        boolean serverTarget, gap, missedCheckpoint, leftAfterArrival;
        Run(String server, String method, Position origin, Position target, Integer ping, long utc, long start) {
            this.server = server; this.method = method; this.origin = origin; this.target = target;
            this.ping = ping; this.utc = utc; this.start = start;
            end = anchor = origin;
        }
    }
    public TeleportAnalyzer(LabConfig config, LabClock clock) {
        this.config = Objects.requireNonNull(config); this.clock = Objects.requireNonNull(clock);
    }
    public boolean recording() { return recording; }
    public boolean observing() { return active != null; }
    public long evictedAttempts() { return evicted; }
    public void setRecording(boolean value) {
        if (!value && active != null) interrupt(Reason.RECORDING_STOPPED);
        recording = value;
    }
    public Optional<UUID> begin(Position origin, Position target, String serverId, String method, Integer pingMs) {
        return begin(origin, target, serverId, method, pingMs, clock.utcMillis(), clock.monotonicMillis());
    }
    /** Adapter may capture clocks at method entry and publish only when Lumen accepts the attempt. */
    public Optional<UUID> begin(Position origin, Position target, String serverId, String method, Integer pingMs,
                                long startedUtcMs, long startedMonotonicMs) {
        if (!recording) return Optional.empty();
        Objects.requireNonNull(origin); Objects.requireNonNull(target);
        if (!serverId.matches("srv_[0-9a-f]{24}") || !Set.of("CLIENT_ONLY", "SINGLEPLAYER").contains(method))
            throw new IllegalArgumentException("Only pseudonymous server IDs and known methods are accepted");
        if (active != null) interrupt(Reason.SUPERSEDED);
        if (startedMonotonicMs > clock.monotonicMillis()) throw new IllegalArgumentException("Start is in the future");
        active = new Run(serverId, method, origin, target, validPing(pingMs), startedUtcMs, startedMonotonicMs);
        event(0, LabEvent.Type.START, origin, validPing(pingMs), null);
        return Optional.of(active.id);
    }
    /** Actual samples are never backfilled at a scheduled time that was not observed. */
    public void sample(Position position, Integer pingMs) {
        if (active == null) return;
        Objects.requireNonNull(position);
        long t = elapsed();
        if (t > config.observationMs() + config.maxSampleGapMs()) {
            active.gap = true;
            finish(Reason.SAMPLE_GAP); // Do not replace end with a position from outside the window.
            return;
        }
        Run r = active;
        if (t - r.lastSample > config.maxSampleGapMs()) r.gap = true;
        boolean at = position.near(r.target, config.targetTolerance());
        if (at && r.arrival == null) r.arrival = t;
        if (!at && r.arrival != null) r.leftAfterArrival = true;
        if (!position.near(r.anchor, config.stationaryTolerance())) {
            r.anchor = position; r.stableSince = t;
        }
        Integer ping = validPing(pingMs);
        if (ping != null) { r.pingSum += ping; r.pingCount++; }
        if (!position.near(r.end, .0001)) event(t, LabEvent.Type.LOCAL_POSITION, position, ping, null);
        // Keep a 20 Hz trace, including unchanged positions, for independent coverage checks.
        event(t, LabEvent.Type.SAMPLE, position, ping, null);
        r.end = position;
        r.lastSample = t;
        while (r.checkpoint < config.checkpointsMs().size() && t >= config.checkpointsMs().get(r.checkpoint)) {
            long due = config.checkpointsMs().get(r.checkpoint++);
            if (t - due > config.maxSampleGapMs()) r.missedCheckpoint = true;
            event(t, LabEvent.Type.CHECKPOINT, position, ping, due);
            if (!at) r.missedCheckpoint = true;
        }
        if (t >= config.observationMs()) finish(null);
    }
    /** Resolved position AFTER vanilla applied a server position packet (including relative flags). */
    public void serverPosition(Position resolved) {
        if (active == null) return;
        if (elapsed() > config.observationMs()) { finish(Reason.MISSING_CHECKPOINTS); return; }
        long t = elapsed();
        if (active.response == null) active.response = t;
        event(t, LabEvent.Type.SERVER_POSITION, resolved, null, null);
        if (resolved.near(active.target, config.targetTolerance())) active.serverTarget = true;
        else {
            active.corrections++;
            event(t, LabEvent.Type.CORRECTION, resolved, null, null);
        }
    }
    public void interrupt(Reason reason) {
        if (active != null) finish(Objects.requireNonNull(reason));
    }
    public void reset() {
        active = null; history.clear(); evicted = 0;
    }
    public List<AttemptSnapshot> snapshots() {
        var copy = new ArrayList<>(history);
        if (active != null) copy.add(snapshot(null, null));
        return List.copyOf(copy);
    }
    private void finish(Reason interruption) {
        Run r = active;
        Status status;
        Reason reason;
        if (interruption != null) { status = Status.UNKNOWN; reason = interruption; }
        else if (r.corrections > 0) { status = Status.CORRECTED; reason = Reason.SERVER_POSITION_OFF_TARGET; }
        else if (r.gap) { status = Status.UNKNOWN; reason = Reason.SAMPLE_GAP; }
        else if (r.serverTarget && r.arrival != null && !r.leftAfterArrival && !r.missedCheckpoint
                && r.checkpoint == config.checkpointsMs().size()
                && r.end.near(r.target, config.targetTolerance()) && elapsed() - r.stableSince >= config.stableMs()) {
            status = Status.SUCCESS_STABLE; reason = Reason.SERVER_TARGET_AND_CONTINUOUS_SAMPLES;
        } else if (r.end.near(r.target, config.targetTolerance()) && !r.serverTarget) {
            status = Status.TIMEOUT; reason = Reason.LOCAL_TARGET_WITHOUT_SERVER_EVIDENCE;
        } else if (!r.end.near(r.origin, config.targetTolerance()) || r.leftAfterArrival) {
            status = Status.PARTIAL; reason = Reason.MOVED_WITHOUT_STABLE_TARGET;
        } else { status = Status.TIMEOUT; reason = Reason.NO_CLEAR_RESULT; }
        event(elapsed(), LabEvent.Type.FINISH, r.end, null, null);
        history.add(snapshot(status, reason));
        if (history.size() > config.maxAttempts()) { history.removeFirst(); evicted++; }
        active = null;
    }
    private AttemptSnapshot snapshot(Status status, Reason reason) {
        Run r = active;
        long elapsed = elapsed();
        Long stable = r.lastSample - r.stableSince >= config.stableMs() ? r.stableSince : null;
        return new AttemptSnapshot(r.id, r.server, r.method, r.utc, elapsed, config, r.origin, r.target, r.end,
                r.ping, r.pingCount == 0 ? null : (double) r.pingSum / r.pingCount, r.arrival, r.response,
                stable, r.serverTarget, r.corrections, r.gap, status, reason, r.events);
    }
    private long elapsed() { return Math.max(0, clock.monotonicMillis() - active.start); }
    private void event(long t, LabEvent.Type type, Position position, Integer ping, Long checkpoint) {
        Run r = active;
        r.events.add(new LabEvent(r.id, r.events.size(), t, r.utc + t, type, position, ping, checkpoint));
    }
    private static Integer validPing(Integer ping) { return ping == null || ping < 0 ? null : ping; }
}
