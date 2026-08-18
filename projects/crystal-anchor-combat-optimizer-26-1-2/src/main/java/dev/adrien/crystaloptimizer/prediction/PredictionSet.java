package dev.adrien.crystaloptimizer.prediction;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record PredictionSet(List<PositionHypothesis> hypotheses, double confidence) {
    public PredictionSet {
        Objects.requireNonNull(hypotheses, "hypotheses");
        if (hypotheses.isEmpty()) {
            throw new IllegalArgumentException("hypotheses must not be empty");
        }
        hypotheses = List.copyOf(hypotheses);
        double totalWeight = hypotheses.stream().mapToDouble(PositionHypothesis::weight).sum();
        if (Math.abs(totalWeight - 1.0) > 1.0e-9) {
            throw new IllegalArgumentException("hypothesis weights must sum to 1.0");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
    }

    public PositionHypothesis likely() {
        return hypotheses.stream()
            .filter(hypothesis -> hypothesis.kind() == PositionHypothesis.Kind.LIKELY)
            .findFirst()
            .orElseGet(() -> hypotheses.stream()
                .max(Comparator.comparingDouble(PositionHypothesis::weight))
                .orElseThrow());
    }
}
