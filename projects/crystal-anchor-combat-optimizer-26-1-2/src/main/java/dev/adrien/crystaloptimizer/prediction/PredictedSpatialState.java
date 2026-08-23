package dev.adrien.crystaloptimizer.prediction;

import java.util.Objects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record PredictedSpatialState(
    PositionHypothesis.Kind kind,
    Vec3 position,
    AABB box,
    Vec3 velocity,
    double weight
) {
    public PredictedSpatialState {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(velocity, "velocity");
        if (!finite(position) || !finite(velocity) || !finite(box)) {
            throw new IllegalArgumentException("predicted spatial state must be finite");
        }
        if (!Double.isFinite(weight) || weight <= 0.0 || weight > 1.0) {
            throw new IllegalArgumentException("weight must be in (0, 1]");
        }
    }

    public PredictedSpatialState withWeight(double nextWeight) {
        return new PredictedSpatialState(kind, position, box, velocity, nextWeight);
    }

    private static boolean finite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }

    private static boolean finite(AABB box) {
        return Double.isFinite(box.minX) && Double.isFinite(box.minY) && Double.isFinite(box.minZ)
            && Double.isFinite(box.maxX) && Double.isFinite(box.maxY) && Double.isFinite(box.maxZ);
    }
}
