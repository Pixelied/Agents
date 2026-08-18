package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.planner.CombatPlan;
import java.util.Objects;

public final class CommitPolicy {
    private final double minimumDeathProbability;
    private final double minimumTotemDenialProbability;
    private final double minimumRobustness;

    public CommitPolicy(
        double minimumDeathProbability,
        double minimumTotemDenialProbability,
        double minimumRobustness
    ) {
        this.minimumDeathProbability = requireProbability(minimumDeathProbability, "minimumDeathProbability");
        this.minimumTotemDenialProbability = requireProbability(
            minimumTotemDenialProbability,
            "minimumTotemDenialProbability"
        );
        this.minimumRobustness = requireProbability(minimumRobustness, "minimumRobustness");
    }

    public boolean shouldCommit(CombatPlan plan) {
        Objects.requireNonNull(plan, "plan");
        double totemDenial = plan.score().totemDenialProbability();
        boolean denialCertified = totemDenial == 0.0 || totemDenial >= minimumTotemDenialProbability;
        return !plan.actions().isEmpty()
            && plan.lethal()
            && !plan.score().unacceptableSelfDeath()
            && plan.dependencyGraph().zeroFeedbackCriticalPath()
            && plan.score().targetDeathProbability() >= minimumDeathProbability
            && denialCertified
            && plan.robustness() >= minimumRobustness
            && plan.score().robustness() >= minimumRobustness;
    }

    private static double requireProbability(double value, String name) {
        if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
        return value;
    }
}
