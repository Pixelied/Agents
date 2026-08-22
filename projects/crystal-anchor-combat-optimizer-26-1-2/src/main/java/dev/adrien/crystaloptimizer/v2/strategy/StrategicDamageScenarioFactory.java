package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.v2.damage.DamageScenario;
import dev.adrien.crystaloptimizer.v2.damage.DamageUncertainty;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/** Pure scenario expansion for observable remote uncertainty. */
final class StrategicDamageScenarioFactory {
    List<DamageScenario> targetScenarios(CombatState state) {
        if (!state.hasSpatialState()) {
            throw new IllegalArgumentException("combat state has no spatial target state");
        }

        SimCombatant observed = state.target();
        var spatial = state.targetSpatial();
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
        List<DamageScenario> scenarios = new ArrayList<>(victims.size());
        for (int index = 0; index < victims.size(); index++) {
            double confidence = reasons.get(index).isEmpty() ? 1.0 : 0.5;
            scenarios.add(new DamageScenario(
                victims.get(index),
                spatial.position(),
                spatial.boundingBox(),
                weight,
                confidence,
                reasons.get(index)
            ));
        }
        return List.copyOf(scenarios);
    }
}
