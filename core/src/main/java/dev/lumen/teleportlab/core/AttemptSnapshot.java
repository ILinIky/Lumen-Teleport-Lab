package dev.lumen.teleportlab.core;

import java.util.List;
import java.util.UUID;

public record AttemptSnapshot(UUID id, String serverId, String method, long startedUtcMs,
                              long observedMs, LabConfig config, Position origin, Position target,
                              Position end, Integer initialPingMs, Double averagePingMs,
                              Long firstLocalArrivalMs, Long firstServerResponseMs, Long stableEndMs,
                              boolean serverTargetObserved, int corrections, boolean sampleGap,
                              Status status, Reason reason, List<LabEvent> events) {
    public AttemptSnapshot { events = List.copyOf(events); }
    public boolean complete() { return status != null; }
    public boolean comparableDimensions() { return origin.dimension().equals(target.dimension()); }
    public Double finalDeviation() {
        return end != null && end.dimension().equals(target.dimension()) ? end.distanceTo(target) : null;
    }
}
