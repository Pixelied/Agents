package dev.adrien.crystaloptimizer.prediction;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.phys.AABB;

public record PredictionSet(List<PredictedSpatialState> hypotheses, double confidence) {
    public PredictionSet {
        Objects.requireNonNull(hypotheses, "hypotheses");
        if (hypotheses.isEmpty()) {
            throw new IllegalArgumentException("hypotheses must not be empty");
        }
        hypotheses = List.copyOf(hypotheses);
        double totalWeight = hypotheses.stream().mapToDouble(PredictedSpatialState::weight).sum();
        if (Math.abs(totalWeight - 1.0) > 1.0e-9) {
            throw new IllegalArgumentException("hypothesis weights must sum to 1.0");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
    }

    /** Compatibility bridge for V1/V2 planner fixtures that still use point hypotheses. */
    public PredictionSet(Collection<PositionHypothesis> legacyHypotheses, double confidence) {
        this(
            legacyHypotheses.stream()
                .map(PredictionSet::fromLegacy)
                .toList(),
            confidence
        );
    }

    public PredictedSpatialState likely() {
        return hypotheses.stream()
            .filter(hypothesis -> hypothesis.kind() == PositionHypothesis.Kind.LIKELY_INERTIAL)
            .findFirst()
            .orElseGet(() -> hypotheses.stream()
                .max(Comparator.comparingDouble(PredictedSpatialState::weight))
                .orElseThrow());
    }

    private static PredictedSpatialState fromLegacy(PositionHypothesis legacy) {
        Objects.requireNonNull(legacy, "legacy hypothesis");
        var position = legacy.position();
        AABB box = new AABB(
            position.x - 0.3,
            position.y,
            position.z - 0.3,
            position.x + 0.3,
            position.y + 1.8,
            position.z + 0.3
        );
        return new PredictedSpatialState(
            modernKind(legacy.kind()),
            position,
            box,
            legacy.velocity(),
            legacy.weight()
        );
    }

    private static PositionHypothesis.Kind modernKind(PositionHypothesis.Kind kind) {
        return switch (kind) {
            case LIKELY, LIKELY_INERTIAL -> PositionHypothesis.Kind.LIKELY_INERTIAL;
            case SLOWED_OR_REVERSAL, BRAKING -> PositionHypothesis.Kind.BRAKING;
            case CONSERVATIVE_BOUND, TURN_OR_REVERSAL -> PositionHypothesis.Kind.TURN_OR_REVERSAL;
        };
    }
}
