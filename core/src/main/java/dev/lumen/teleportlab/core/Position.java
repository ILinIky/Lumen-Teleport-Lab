package dev.lumen.teleportlab.core;

import java.util.Objects;

/** Coordinates in Minecraft blocks; dimension participates in arrival checks. */
public record Position(String dimension, double x, double y, double z) {
    public Position {
        Objects.requireNonNull(dimension);
        if (dimension.isBlank() || !Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z))
            throw new IllegalArgumentException("Finite coordinates and a dimension are required");
    }
    public double horizontalTo(Position p) { return Math.hypot(x - p.x, z - p.z); }
    public double distanceTo(Position p) { return Math.hypot(horizontalTo(p), y - p.y); }
    public boolean near(Position p, double tolerance) {
        return dimension.equals(p.dimension) && distanceTo(p) <= tolerance;
    }
}
