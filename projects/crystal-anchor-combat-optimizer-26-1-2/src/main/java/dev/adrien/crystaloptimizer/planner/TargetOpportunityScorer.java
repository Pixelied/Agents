package dev.adrien.crystaloptimizer.planner;

import java.util.Objects;
import java.util.UUID;

public final class TargetOpportunityScorer {
    private TargetOpportunityScorer() {
    }

    public static TargetPriority priority(
        UUID targetId,
        CombatPlan plan,
        double threatScore,
        double distance
    ) {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(plan, "plan");
        return new TargetPriority(
            targetId,
            killOpportunity(plan),
            clamp01(threatScore),
            Math.max(0.0, distance)
        );
    }

    public static double killOpportunity(CombatPlan plan) {
        Objects.requireNonNull(plan, "plan");
        PlanScore score = plan.score();
        if (score.unacceptableSelfDeath()) {
            return 0.0;
        }

        double robustness = clamp01(Math.min(plan.robustness(), score.robustness()));
        double safety = 1.0 - clamp01(score.selfDamageRisk());
        double feedbackQuality = 1.0 / (1.0 + score.networkDependencyPenalty());

        if (plan.lethal() && score.targetDeathProbability() > 0.0) {
            double speed = score.timeToKillActions() == Integer.MAX_VALUE
                ? 0.0
                : 1.0 / (1.0 + Math.max(0, score.timeToKillActions()));
            double opportunity = 0.70
                + 0.12 * clamp01(score.targetDeathProbability())
                + 0.04 * clamp01(score.totemDenialProbability())
                + 0.05 * speed
                + 0.04 * robustness
                + 0.03 * safety
                + 0.02 * feedbackQuality;
            return clamp01(opportunity);
        }

        double geometry = 1.0 - Math.exp(-Math.max(0.0, score.futureGeometryValue()) / 2.0);
        double opportunity = 0.44 * clamp01(score.threatNeutralization())
            + 0.08 * robustness
            + 0.05 * geometry
            + 0.07 * safety
            + 0.04 * feedbackQuality;
        return Math.min(0.68, clamp01(opportunity));
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
