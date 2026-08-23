package dev.adrien.crystaloptimizer.client.intel;

import dev.adrien.crystaloptimizer.prediction.MovementSample;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Bounded observable movement history for remote players. */
public final class TargetMotionTracker {
    public static final int MAX_SAMPLES = 12;
    private static final long MAX_SAMPLE_GAP_NANOS = 1_000_000_000L;
    private static final double CORRECTION_DISTANCE_SQUARED = 64.0;
    private static final double NANOS_PER_TICK = 50_000_000.0;
    private static final TargetMotionTracker INSTANCE = new TargetMotionTracker();

    private final Map<UUID, ArrayDeque<MovementSample>> histories = new HashMap<>();

    public TargetMotionTracker() {
    }

    public static TargetMotionTracker instance() {
        return INSTANCE;
    }

    public synchronized void observe(
        UUID targetId,
        Vec3 position,
        AABB box,
        Vec3 observedVelocity,
        boolean correction,
        long nowNanos
    ) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(observedVelocity, "observedVelocity");
        if (nowNanos < 0L) {
            throw new IllegalArgumentException("nowNanos must be non-negative");
        }
        requireFinite(position, "position");
        requireFinite(observedVelocity, "observedVelocity");
        requireFinite(box, "box");

        ArrayDeque<MovementSample> history = histories.computeIfAbsent(
            targetId,
            ignored -> new ArrayDeque<>(MAX_SAMPLES)
        );
        MovementSample previous = history.peekLast();
        if (previous != null && nowNanos <= previous.timestampNanos()) {
            return;
        }

        boolean stale = previous != null
            && nowNanos - previous.timestampNanos() > MAX_SAMPLE_GAP_NANOS;
        boolean largeJump = previous != null
            && previous.position().distanceToSqr(position) > CORRECTION_DISTANCE_SQUARED;
        if (correction || stale || largeJump) {
            history.clear();
            previous = null;
        }

        Vec3 velocity = observedVelocity;
        if (previous != null) {
            double elapsedTicks = (nowNanos - previous.timestampNanos()) / NANOS_PER_TICK;
            if (elapsedTicks > 1.0E-9) {
                velocity = position.subtract(previous.position()).scale(1.0 / elapsedTicks);
            }
        }
        history.addLast(new MovementSample(nowNanos, position, velocity));
        while (history.size() > MAX_SAMPLES) {
            history.removeFirst();
        }
    }

    public synchronized List<MovementSample> snapshot(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        ArrayDeque<MovementSample> history = histories.get(targetId);
        return history == null ? List.of() : List.copyOf(history);
    }

    public synchronized void remove(UUID targetId) {
        if (targetId != null) {
            histories.remove(targetId);
        }
    }

    public synchronized void clear() {
        histories.clear();
    }

    private static void requireFinite(Vec3 vector, String name) {
        if (!Double.isFinite(vector.x) || !Double.isFinite(vector.y) || !Double.isFinite(vector.z)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireFinite(AABB box, String name) {
        if (!Double.isFinite(box.minX) || !Double.isFinite(box.minY) || !Double.isFinite(box.minZ)
            || !Double.isFinite(box.maxX) || !Double.isFinite(box.maxY) || !Double.isFinite(box.maxZ)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
