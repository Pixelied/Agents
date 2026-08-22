package dev.adrien.crystaloptimizer.v2.debug;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Immutable deterministic summary of one combat decision.
 *
 * <p>Keys are normalized on construction so diagnostic output is stable enough to diff and
 * replay. Timings stored here are measured durations/distributions, never absolute wall-clock
 * timestamps used to influence a replay decision.</p>
 */
public record DecisionTrace(
    String snapshotHash,
    String configHash,
    Map<String, Double> targetPreScores,
    Map<String, Integer> candidateCounts,
    List<String> finalistProjections,
    UUID targetId,
    String chosenDecisionKey,
    String decisionClass,
    List<String> rejectionReasons,
    Map<String, Double> timingMillis,
    long worldRevision,
    long targetRevision,
    long inventoryRevision,
    long configRevision,
    long strategicDurationNanos,
    long reactiveDurationNanos
) {
    public DecisionTrace {
        snapshotHash = requireText(snapshotHash, "snapshotHash");
        configHash = requireText(configHash, "configHash");
        Objects.requireNonNull(targetPreScores, "targetPreScores");
        Objects.requireNonNull(candidateCounts, "candidateCounts");
        Objects.requireNonNull(finalistProjections, "finalistProjections");
        Objects.requireNonNull(rejectionReasons, "rejectionReasons");
        Objects.requireNonNull(timingMillis, "timingMillis");
        chosenDecisionKey = requireText(chosenDecisionKey, "chosenDecisionKey");
        decisionClass = requireText(decisionClass, "decisionClass");
        targetPreScores = sortedMap(targetPreScores);
        candidateCounts = sortedMap(candidateCounts);
        timingMillis = sortedMap(timingMillis);
        finalistProjections = List.copyOf(finalistProjections);
        rejectionReasons = List.copyOf(rejectionReasons);
        if (worldRevision < 0L || targetRevision < 0L || inventoryRevision < 0L || configRevision < 0L
            || strategicDurationNanos < 0L || reactiveDurationNanos < 0L) {
            throw new IllegalArgumentException("trace revisions and durations must be non-negative");
        }
    }

    public static DecisionTrace minimal(String chosenDecisionKey) {
        return new DecisionTrace(
            "unknown",
            "unknown",
            Map.of(),
            Map.of(),
            List.of(),
            null,
            chosenDecisionKey,
            "unknown",
            List.of(),
            Map.of(),
            0L,
            0L,
            0L,
            0L,
            0L,
            0L
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static <V> Map<String, V> sortedMap(Map<String, V> source) {
        TreeMap<String, V> sorted = new TreeMap<>();
        source.forEach((key, value) -> sorted.put(
            Objects.requireNonNull(key, "trace map key"),
            Objects.requireNonNull(value, "trace map value")
        ));
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }
}
