package dev.adrien.crystaloptimizer.v2.damage;

import dev.adrien.crystaloptimizer.sim.damage.DamageRequest;
import dev.adrien.crystaloptimizer.sim.damage.DamageResult;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionDamageCalculator26;
import dev.adrien.crystaloptimizer.sim.damage.VanillaDamageSimulator;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

public final class DamageEngine {
    public DamageEstimate estimate(
        ExplosionContext explosion,
        CombatState state,
        List<DamageScenario> scenarios,
        long geometryRevision,
        long combatRevision
    ) {
        Objects.requireNonNull(explosion, "explosion");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(scenarios, "scenarios");
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("no damage scenarios");
        }

        float lower = Float.POSITIVE_INFINITY;
        float upper = Float.NEGATIVE_INFINITY;
        double weightedDamage = 0.0;
        double weightedConfidence = 0.0;
        double totalWeight = 0.0;
        EnumSet<DamageUncertainty> reasons = EnumSet.noneOf(DamageUncertainty.class);

        for (DamageScenario scenario : scenarios) {
            Objects.requireNonNull(scenario, "damage scenario");
            float incoming = ExplosionDamageCalculator26.incoming(
                explosion,
                scenario.box(),
                scenario.position(),
                state.geometry()
            );
            DamageResult result = VanillaDamageSimulator.apply(
                scenario.victim(),
                DamageRequest.explosion(incoming)
                    .withDifficulty(state.base().difficulty())
                    .withSourcePosition(explosion.center())
            );
            float damage = result.trace().healthDamage();
            lower = Math.min(lower, damage);
            upper = Math.max(upper, damage);
            weightedDamage += damage * scenario.probabilityWeight();
            weightedConfidence += scenario.confidence() * scenario.probabilityWeight();
            totalWeight += scenario.probabilityWeight();
            reasons.addAll(scenario.uncertainties());
        }

        return new DamageEstimate(
            lower,
            (float) (weightedDamage / totalWeight),
            upper,
            weightedConfidence / totalWeight,
            reasons,
            geometryRevision,
            combatRevision
        );
    }
}
