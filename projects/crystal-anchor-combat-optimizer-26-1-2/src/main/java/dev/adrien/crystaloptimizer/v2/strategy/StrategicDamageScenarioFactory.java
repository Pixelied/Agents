package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.prediction.PredictedSpatialState;
import dev.adrien.crystaloptimizer.prediction.PredictionSet;
import dev.adrien.crystaloptimizer.prediction.TargetPredictionModel;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.v2.damage.DamageScenario;
import dev.adrien.crystaloptimizer.v2.damage.DamageUncertainty;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Pure scenario expansion for observable combat and movement uncertainty. */
final class StrategicDamageScenarioFactory {
    private static final double EXPECTED_HORIZON_WEIGHT = 0.65;
    private static final double P90_HORIZON_WEIGHT = 0.35;
    private static final double HORIZON_EPSILON_MILLIS = 0.001;

    private final TargetPredictionModel predictions;

    StrategicDamageScenarioFactory() {
        this(new TargetPredictionModel());
    }

    StrategicDamageScenarioFactory(TargetPredictionModel predictions) {
        this.predictions = Objects.requireNonNull(predictions, "predictions");
    }

    List<DamageScenario> targetScenarios(CombatState state) {
        return currentSpatialScenarios(state);
    }

    List<DamageScenario> targetScenarios(
        StrategicSnapshot snapshot,
        CombatState state,
        SequenceTiming timing
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(timing, "timing");
        if (!state.hasSpatialState()) {
            throw new IllegalArgumentException("combat state has no spatial target state");
        }
        if (timing.p90Millis() <= 0.0
            || snapshot.movementHistory().get(state.targetId()) == null
            || snapshot.movementHistory().get(state.targetId()).size() < 2) {
            return currentSpatialScenarios(state);
        }

        List<SpatialVariant> spatial = predictionVariants(snapshot, state, timing);
        if (spatial.isEmpty()) {
            return currentSpatialScenarios(state);
        }
        return crossProduct(state, combatVariants(state.target()), spatial);
    }

    private List<SpatialVariant> predictionVariants(
        StrategicSnapshot snapshot,
        CombatState state,
        SequenceTiming timing
    ) {
        boolean hasExpected = Double.isFinite(timing.expectedMillis()) && timing.expectedMillis() > 0.0;
        boolean hasP90 = Double.isFinite(timing.p90Millis()) && timing.p90Millis() > 0.0;
        if (!hasExpected && !hasP90) {
            return List.of();
        }

        boolean distinctP90 = hasExpected && hasP90
            && timing.p90Millis() - timing.expectedMillis() > HORIZON_EPSILON_MILLIS;
        ArrayList<SpatialVariant> result = new ArrayList<>();
        if (hasExpected) {
            double horizonWeight = distinctP90 ? EXPECTED_HORIZON_WEIGHT : 1.0;
            addPredictionSet(
                result,
                predictions.predict(
                    snapshot,
                    state.targetId(),
                    Duration.ofNanos(millisToNanos(timing.expectedMillis()))
                ),
                horizonWeight,
                timing.confidence()
            );
        }
        if (hasP90 && (!hasExpected || distinctP90)) {
            double horizonWeight = hasExpected ? P90_HORIZON_WEIGHT : 1.0;
            addPredictionSet(
                result,
                predictions.predict(
                    snapshot,
                    state.targetId(),
                    Duration.ofNanos(millisToNanos(timing.p90Millis()))
                ),
                horizonWeight,
                timing.confidence()
            );
        }
        return normalizeSpatial(result);
    }

    private static void addPredictionSet(
        List<SpatialVariant> output,
        Optional<PredictionSet> prediction,
        double horizonWeight,
        double timingConfidence
    ) {
        if (prediction.isEmpty()) {
            return;
        }
        PredictionSet set = prediction.orElseThrow();
        double confidence = clamp01(set.confidence() * timingConfidence);
        for (PredictedSpatialState hypothesis : set.hypotheses()) {
            output.add(new SpatialVariant(
                hypothesis.position(),
                hypothesis.box(),
                horizonWeight * hypothesis.weight(),
                confidence,
                true
            ));
        }
    }

    private List<DamageScenario> currentSpatialScenarios(CombatState state) {
        if (!state.hasSpatialState()) {
            throw new IllegalArgumentException("combat state has no spatial target state");
        }
        var spatial = state.targetSpatial();
        return crossProduct(
            state,
            combatVariants(state.target()),
            List.of(new SpatialVariant(
                spatial.position(),
                spatial.boundingBox(),
                1.0,
                1.0,
                false
            ))
        );
    }

    private static List<CombatVariant> combatVariants(SimCombatant observed) {
        boolean unknownThreshold = observed.hurtWindow().invulnerableTime() > 10
            && !observed.hurtWindow().lastHurtKnown();
        boolean unknownAbsorption = observed.absorption() > 0.0f;

        List<SimCombatant> victims = new ArrayList<>();
        List<EnumSet<DamageUncertainty>> reasons = new ArrayList<>();
        victims.add(observed);
        reasons.add(EnumSet.noneOf(DamageUncertainty.class));

        if (unknownThreshold) {
            victims.clear();
            reasons.clear();
            victims.add(observed.withHurtWindow(new HurtWindowState(
                observed.hurtWindow().invulnerableTime(),
                HurtThresholdEstimate.MAX_CRYSTAL_HARD_INCOMING
            )));
            reasons.add(EnumSet.of(DamageUncertainty.HURT_THRESHOLD_UNKNOWN));
            victims.add(observed.withHurtWindow(new HurtWindowState(
                observed.hurtWindow().invulnerableTime(),
                0.0f
            )));
            reasons.add(EnumSet.of(DamageUncertainty.HURT_THRESHOLD_UNKNOWN));
        }

        if (unknownAbsorption) {
            int originalSize = victims.size();
            for (int index = 0; index < originalSize; index++) {
                SimCombatant noAbsorption = victims.get(index).withAbsorption(0.0f);
                EnumSet<DamageUncertainty> noAbsorptionReasons = EnumSet.copyOf(reasons.get(index));
                noAbsorptionReasons.add(DamageUncertainty.ABSORPTION_UNKNOWN);
                reasons.get(index).add(DamageUncertainty.ABSORPTION_UNKNOWN);
                victims.add(noAbsorption);
                reasons.add(noAbsorptionReasons);
            }
        }

        double weight = 1.0 / victims.size();
        ArrayList<CombatVariant> variants = new ArrayList<>(victims.size());
        for (int index = 0; index < victims.size(); index++) {
            double confidence = reasons.get(index).isEmpty() ? 1.0 : 0.5;
            variants.add(new CombatVariant(
                victims.get(index),
                weight,
                confidence,
                EnumSet.copyOf(reasons.get(index))
            ));
        }
        return List.copyOf(variants);
    }

    private static List<DamageScenario> crossProduct(
        CombatState state,
        List<CombatVariant> combat,
        List<SpatialVariant> spatial
    ) {
        ArrayList<DamageScenario> scenarios = new ArrayList<>(combat.size() * spatial.size());
        for (CombatVariant combatVariant : combat) {
            for (SpatialVariant spatialVariant : spatial) {
                EnumSet<DamageUncertainty> uncertainties = EnumSet.copyOf(combatVariant.uncertainties());
                if (spatialVariant.predicted()) {
                    uncertainties.add(DamageUncertainty.PREDICTED_POSITION);
                }
                scenarios.add(new DamageScenario(
                    combatVariant.victim(),
                    spatialVariant.position(),
                    spatialVariant.box(),
                    combatVariant.weight() * spatialVariant.weight(),
                    clamp01(combatVariant.confidence() * spatialVariant.confidence()),
                    uncertainties
                ));
            }
        }
        return List.copyOf(scenarios);
    }

    private static List<SpatialVariant> normalizeSpatial(List<SpatialVariant> variants) {
        if (variants.isEmpty()) {
            return List.of();
        }
        double total = variants.stream().mapToDouble(SpatialVariant::weight).sum();
        if (!Double.isFinite(total) || total <= 0.0) {
            return List.of();
        }
        return variants.stream()
            .map(variant -> new SpatialVariant(
                variant.position(),
                variant.box(),
                variant.weight() / total,
                variant.confidence(),
                variant.predicted()
            ))
            .toList();
    }

    private static long millisToNanos(double millis) {
        if (!Double.isFinite(millis) || millis <= 0.0) {
            return 0L;
        }
        double nanos = millis * 1_000_000.0;
        return nanos >= Long.MAX_VALUE ? Long.MAX_VALUE : Math.max(0L, Math.round(nanos));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record CombatVariant(
        SimCombatant victim,
        double weight,
        double confidence,
        EnumSet<DamageUncertainty> uncertainties
    ) {
    }

    private record SpatialVariant(
        net.minecraft.world.phys.Vec3 position,
        net.minecraft.world.phys.AABB box,
        double weight,
        double confidence,
        boolean predicted
    ) {
    }
}
