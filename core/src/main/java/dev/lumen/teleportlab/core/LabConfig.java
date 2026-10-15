package dev.lumen.teleportlab.core;

import java.util.List;

public record LabConfig(long observationMs, List<Long> checkpointsMs, double targetTolerance,
                        double stationaryTolerance, long stableMs, long maxSampleGapMs, int maxAttempts) {
    public LabConfig {
        checkpointsMs = List.copyOf(checkpointsMs);
        if (observationMs < 1000 || observationMs > 120000 || stableMs < 100 || stableMs > observationMs
                || maxSampleGapMs < 50 || maxSampleGapMs > 2000 || maxAttempts < 1 || maxAttempts > 2000
                || !Double.isFinite(targetTolerance) || targetTolerance <= 0 || targetTolerance > 10
                || !Double.isFinite(stationaryTolerance) || stationaryTolerance <= 0 || stationaryTolerance > targetTolerance
                || checkpointsMs.isEmpty() || checkpointsMs.getLast() != observationMs)
            throw new IllegalArgumentException("Invalid observation configuration");
        long previous = 0;
        for (long t : checkpointsMs) {
            if (t <= previous || t > observationMs) throw new IllegalArgumentException("Checkpoints must increase");
            previous = t;
        }
    }
    public static LabConfig defaults() {
        return new LabConfig(10000, List.of(1000L, 3000L, 5000L, 10000L), 1.5, .10, 1000, 500, 500);
    }
}
