package dev.lumen.teleportlab.core;

import java.util.UUID;

public record LabEvent(UUID attemptId, int sequence, long elapsedMs, long utcMs, Type type,
                       Position position, Integer pingMs, Long checkpointMs) {
    public enum Type { START, LOCAL_POSITION, SAMPLE, CHECKPOINT, SERVER_POSITION, CORRECTION, FINISH }
}
