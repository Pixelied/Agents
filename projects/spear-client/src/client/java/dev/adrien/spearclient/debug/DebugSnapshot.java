package dev.adrien.spearclient.debug;

import dev.adrien.spearclient.network.ServerStateTracker;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record DebugSnapshot(
    String targetName,
    int targetId,
    double targetDistance,
    String kind,
    String phase,
    int movementPacketsSent,
    boolean corrected,
    int correctionCount,
    double baseSpearRange,
    double expectedForwardKnownMovement,
    double predictedRawDamage,
    double predictedSourceModelReach
) {
    private static final double BASE_SPEAR_RANGE = 4.5;

    public DebugSnapshot {
        targetName = Objects.requireNonNullElse(targetName, "none");
        kind = Objects.requireNonNullElse(kind, "NONE");
        phase = Objects.requireNonNullElse(phase, "IDLE");
    }

    public static DebugSnapshot from(
        ServerStateTracker.Snapshot tracker,
        String targetName,
        double targetDistance
    ) {
        Objects.requireNonNull(tracker, "tracker");
        return new DebugSnapshot(
            targetName,
            tracker.targetId(),
            targetDistance,
            tracker.kind(),
            tracker.phase(),
            tracker.movementPacketsSent(),
            tracker.corrected(),
            tracker.correctionCount(),
            BASE_SPEAR_RANGE,
            tracker.expectedForwardKnownMovement(),
            tracker.predictedRawDamage(),
            tracker.predictedSourceModelReach()
        );
    }

    public List<String> lines() {
        String target = targetId >= 0
            ? String.format(Locale.ROOT, "Target: %s (#%d) @ %s", targetName, targetId, format(targetDistance))
            : "Target: none";
        return List.of(
            target,
            "Sequence: " + kind + " / " + phase,
            "Movement packets: " + movementPacketsSent,
            "Corrections: " + correctionCount + (corrected ? " (detected)" : ""),
            "Base spear range: " + format(baseSpearRange),
            "Expected known forward: " + format(expectedForwardKnownMovement),
            "Predicted raw damage: " + format(predictedRawDamage),
            "Predicted source-model reach: " + format(predictedSourceModelReach)
        );
    }

    private static String format(double value) {
        return Double.isFinite(value)
            ? String.format(Locale.ROOT, "%.2f", value)
            : "n/a";
    }
}
