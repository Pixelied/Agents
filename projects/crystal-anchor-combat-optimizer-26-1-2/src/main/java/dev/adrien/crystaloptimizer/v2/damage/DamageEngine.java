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

        float effectiveLower = Float.POSITIVE_INFINITY;
        float effectiveUpper = Float.NEGATIVE_INFINITY;
        float healthLower = Float.POSITIVE_INFINITY;
        float healthUpper = Float.NEGATIVE_INFINITY;
        float postMitigationLower = Float.POSITIVE_INFINITY;
        float postMitigationUpper = Float.NEGATIVE_INFINITY;
        double weightedEffective = 0.0;
        double weightedHealth = 0.0;
        double weightedPostMitigation = 0.0;
        double weightedPop = 0.0;
        double weightedKill = 0.0;
        double weightedConfidence = 0.0;
        double totalWeight = 0.0;
        EnumSet<DamageUncertainty> reasons = EnumSet.noneOf(DamageUncertainty.class);

        for (DamageScenario scenario : scenarios) {
            Objects.requireNonNull(scenario, "damage scenario");
            float rawIncoming = ExplosionDamageCalculator26.incoming(
                explosion,
                scenario.box(),
                scenario.position(),
                state.geometry()
            );
            DamageResult result = VanillaDamageSimulator.apply(
                scenario.victim(),
                DamageRequest.explosion(rawIncoming)
                    .withDifficulty(state.base().difficulty())
                    .withSourcePosition(explosion.center())
            );
            DamageProjection projection = projection(result);
            double weight = scenario.probabilityWeight();

            effectiveLower = Math.min(effectiveLower, projection.effectiveTotalLoss());
            effectiveUpper = Math.max(effectiveUpper, projection.effectiveTotalLoss());
            healthLower = Math.min(healthLower, projection.healthLoss());
            healthUpper = Math.max(healthUpper, projection.healthLoss());
            postMitigationLower = Math.min(postMitigationLower, projection.postMitigationIncoming());
            postMitigationUpper = Math.max(postMitigationUpper, projection.postMitigationIncoming());

            weightedEffective += projection.effectiveTotalLoss() * weight;
            weightedHealth += projection.healthLoss() * weight;
            weightedPostMitigation += projection.postMitigationIncoming() * weight;
            weightedPop += (projection.totemTriggered() ? 1.0 : 0.0) * weight;
            weightedKill += (result.trace().dead() ? 1.0 : 0.0) * weight;
            weightedConfidence += scenario.confidence() * weight;
            totalWeight += weight;
            reasons.addAll(scenario.uncertainties());
        }

        return new DamageEstimate(
            effectiveLower,
            (float) (weightedEffective / totalWeight),
            effectiveUpper,
            healthLower,
            (float) (weightedHealth / totalWeight),
            healthUpper,
            postMitigationLower,
            (float) (weightedPostMitigation / totalWeight),
            postMitigationUpper,
            weightedPop / totalWeight,
            weightedKill / totalWeight,
            weightedConfidence / totalWeight,
            reasons,
            geometryRevision,
            combatRevision
        );
    }

    private static DamageProjection projection(DamageResult result) {
        var trace = result.trace();
        float postHitEffectiveHealth = result.target().health() + result.target().absorption();
        float nextHurtThreshold = result.target().hurtWindow().lastHurtKnown()
            ? result.target().hurtWindow().lastHurt()
            : 0.0f;
        return new DamageProjection(
            trace.rawIncoming(),
            trace.incoming(),
            trace.absorptionConsumed(),
            trace.healthDamage(),
            postHitEffectiveHealth,
            nextHurtThreshold,
            trace.totemTriggered()
        );
    }
}
