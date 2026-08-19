package dev.pixelied.survival.core;

public record AabbSnapshot(
    double minX,
    double minY,
    double minZ,
    double maxX,
    double maxY,
    double maxZ
) {
    public AabbSnapshot {
        if (minX > maxX || minY > maxY || minZ > maxZ) {
            throw new IllegalArgumentException("AABB minimums must not exceed maximums");
        }
    }
}
