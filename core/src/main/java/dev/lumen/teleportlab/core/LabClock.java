package dev.lumen.teleportlab.core;

public interface LabClock {
    long utcMillis();
    long monotonicMillis();
    static LabClock system() {
        return new LabClock() {
            public long utcMillis() { return System.currentTimeMillis(); }
            public long monotonicMillis() { return System.nanoTime() / 1_000_000; }
        };
    }
}
