package dev.adrien.crystaloptimizer.prediction;

import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Worker-owned prediction state: per-target calibration plus bounded future probes. */
public final class TargetPredictionModel {
    private static final long MAX_HISTORY_AGE_NANOS = 500_000_000L;
    private static final long MAX_PROBE_AGE_NANOS = 2_000_000_000L;
    private static final int MAX_PENDING_PROBES = 4;

    private final TargetPredictor predictor = new TargetPredictor();
    private final Map<UUID, PredictionCalibration> calibrations = new HashMap<>();
    private final Map<UUID, ArrayDeque<PredictionProbe>> pendingProbes = new HashMap<>();

    public Optional<PredictionSet> predict(
        StrategicSnapshot snapshot,
        UUID targetId,
        Duration actionDelay
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(actionDelay, "actionDelay");
        if (actionDelay.isNegative()) {
            throw new IllegalArgumentException("actionDelay must be non-negative");
        }

        List<MovementSample> history = ordered(snapshot.movementHistory().get(targetId));
        if (history.size() < 2) {
            return Optional.empty();
        }
        MovementSample latest = history.getLast();
        if (snapshot.capturedAtNanos() < latest.timestampNanos()) {
            return Optional.empty();
        }
        long historyAge = snapshot.capturedAtNanos() - latest.timestampNanos();
        if (historyAge > MAX_HISTORY_AGE_NANOS) {
            return Optional.empty();
        }

        var spatial = snapshot.combat().spatial().get(targetId);
        if (spatial == null) {
            return Optional.empty();
        }
        long delayNanos;
        try {
            delayNanos = actionDelay.toNanos();
        } catch (ArithmeticException overflow) {
            return Optional.empty();
        }
        long totalNanos = saturatingAdd(historyAge, delayNanos);
        Duration totalHorizon = Duration.ofNanos(totalNanos);
        AABB startBox = alignBox(spatial.boundingBox(), spatial.position(), latest.position());
        PredictionSet prediction = predictor.predict(
            history,
            snapshot.combat().region(),
            startBox,
            totalHorizon,
            calibration(targetId)
        );
        if (delayNanos > 0L) {
            queueProbe(
                targetId,
                saturatingAdd(latest.timestampNanos(), totalNanos),
                prediction
            );
        }
        return Optional.of(prediction);
    }

    public void observeSnapshot(StrategicSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        for (var entry : pendingProbes.entrySet()) {
            UUID targetId = entry.getKey();
            ArrayDeque<PredictionProbe> probes = entry.getValue();
            List<MovementSample> history = ordered(snapshot.movementHistory().get(targetId));
            if (history.isEmpty()) {
                dropExpired(probes, snapshot.capturedAtNanos());
                continue;
            }

            while (!probes.isEmpty()) {
                PredictionProbe probe = probes.peekFirst();
                MovementSample observation = firstAtOrAfter(history, probe.targetAtNanos());
                if (observation == null) {
                    if (snapshot.capturedAtNanos() - probe.targetAtNanos() > MAX_PROBE_AGE_NANOS) {
                        probes.removeFirst();
                        continue;
                    }
                    break;
                }

                PredictionCalibration calibration = calibration(targetId);
                for (PredictedSpatialState hypothesis : probe.prediction().hypotheses()) {
                    calibration.observeError(
                        hypothesis.kind(),
                        hypothesis.position().distanceTo(observation.position())
                    );
                }
                probes.removeFirst();
            }
        }
        pendingProbes.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public Map<PositionHypothesis.Kind, Double> weights(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        return calibration(targetId).normalizedWeights();
    }

    public int pendingProbeCount(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        ArrayDeque<PredictionProbe> probes = pendingProbes.get(targetId);
        return probes == null ? 0 : probes.size();
    }

    private PredictionCalibration calibration(UUID targetId) {
        return calibrations.computeIfAbsent(targetId, ignored -> PredictionCalibration.defaults());
    }

    private void queueProbe(UUID targetId, long targetAtNanos, PredictionSet prediction) {
        ArrayDeque<PredictionProbe> probes = pendingProbes.computeIfAbsent(
            targetId,
            ignored -> new ArrayDeque<>(MAX_PENDING_PROBES)
        );
        for (PredictionProbe existing : probes) {
            if (existing.targetAtNanos() == targetAtNanos) {
                return;
            }
        }
        probes.addLast(new PredictionProbe(targetAtNanos, prediction));
        while (probes.size() > MAX_PENDING_PROBES) {
            probes.removeFirst();
        }
    }

    private static void dropExpired(ArrayDeque<PredictionProbe> probes, long nowNanos) {
        while (!probes.isEmpty()
            && nowNanos >= probes.peekFirst().targetAtNanos()
            && nowNanos - probes.peekFirst().targetAtNanos() > MAX_PROBE_AGE_NANOS) {
            probes.removeFirst();
        }
    }

    private static MovementSample firstAtOrAfter(List<MovementSample> history, long targetAtNanos) {
        for (MovementSample sample : history) {
            if (sample.timestampNanos() >= targetAtNanos) {
                return sample;
            }
        }
        return null;
    }

    private static List<MovementSample> ordered(List<MovementSample> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.stream()
            .filter(Objects::nonNull)
            .sorted(Comparator.comparingLong(MovementSample::timestampNanos))
            .toList();
    }

    private static AABB alignBox(AABB box, Vec3 boxPosition, Vec3 predictionStart) {
        Vec3 delta = predictionStart.subtract(boxPosition);
        return box.move(delta);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private record PredictionProbe(long targetAtNanos, PredictionSet prediction) {
        private PredictionProbe {
            if (targetAtNanos < 0L) {
                throw new IllegalArgumentException("targetAtNanos must be non-negative");
            }
            Objects.requireNonNull(prediction, "prediction");
        }
    }
}
