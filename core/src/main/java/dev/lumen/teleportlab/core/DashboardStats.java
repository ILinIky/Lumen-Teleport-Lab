package dev.lumen.teleportlab.core;

import java.util.List;

public record DashboardStats(int attempts, long stable, long corrected, long unclear, long completed,
                             double successRate, Double averagePingMs) {
    public static DashboardStats of(List<AttemptSnapshot> attempts) {
        var done = attempts.stream().filter(AttemptSnapshot::complete).toList();
        long stable = done.stream().filter(a -> a.status() == Status.SUCCESS_STABLE).count();
        var pings = done.stream().map(AttemptSnapshot::averagePingMs).filter(p -> p != null).mapToDouble(Double::doubleValue).average();
        return new DashboardStats(attempts.size(), stable,
                done.stream().filter(a -> a.status() == Status.CORRECTED).count(),
                done.stream().filter(a -> a.status() == Status.UNKNOWN || a.status() == Status.TIMEOUT).count(),
                done.size(), done.isEmpty() ? 0 : 100.0 * stable / done.size(), pings.isPresent() ? pings.getAsDouble() : null);
    }
}
