package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class FastOpportunitySelector {
    private static final double LETHAL_CONFIDENCE_FLOOR = 0.80;

    public Optional<DamageOpportunity> select(
        List<DamageOpportunity> opportunities,
        SelectionContext context
    ) {
        Objects.requireNonNull(opportunities, "opportunities");
        Objects.requireNonNull(context, "context");
        return opportunities.stream()
            .filter(Objects::nonNull)
            .max(comparator(context));
    }

    public static float effectiveLowerBound(
        DamageEstimate estimate,
        SelectionContext context
    ) {
        Objects.requireNonNull(estimate, "estimate");
        Objects.requireNonNull(context, "context");
        return Math.max(0.0f, estimate.lowerBound());
    }

    public static float effectiveExpected(
        DamageEstimate estimate,
        SelectionContext context
    ) {
        Objects.requireNonNull(estimate, "estimate");
        Objects.requireNonNull(context, "context");
        return Math.max(0.0f, estimate.expected());
    }

    private Comparator<DamageOpportunity> comparator(SelectionContext context) {
        return Comparator
            .comparingInt((DamageOpportunity opportunity) -> priorityClass(opportunity, context))
            .thenComparingDouble(opportunity -> effectiveLowerRate(opportunity, context))
            .thenComparingDouble(opportunity -> effectiveExpectedRate(opportunity, context))
            .thenComparingDouble(opportunity -> opportunity.targetDamage().lowerBound())
            .thenComparingDouble(opportunity -> opportunity.targetDamage().expected())
            .thenComparingInt(opportunity -> -opportunity.timing().hardFeedbackBoundaries())
            .thenComparingDouble(opportunity -> -safeCompletionMillis(opportunity))
            .thenComparingDouble(opportunity -> -opportunity.resources().cost())
            .thenComparingDouble(opportunity -> -opportunity.worstCaseSelfDamage());
    }

    private int priorityClass(DamageOpportunity opportunity, SelectionContext context) {
        boolean certifiedLethal = opportunity.lethal()
            && opportunity.targetDamage().killProbability() == 1.0
            && opportunity.targetDamage().confidence() >= LETHAL_CONFIDENCE_FLOOR;
        if (certifiedLethal) {
            return 5;
        }
        boolean certifiedPop = opportunity.popsTotem()
            && opportunity.targetDamage().popProbability() == 1.0
            && opportunity.timing().hardFeedbackBoundaries() == 0
            && opportunity.targetDamage().confidence() >= LETHAL_CONFIDENCE_FLOOR;
        if (certifiedPop) {
            return 4;
        }
        if (effectiveLowerBound(opportunity.targetDamage(), context) > 0.0f) {
            return 3;
        }
        if (context.strategy() == OptimizerStrategy.SAFE) {
            return opportunity.worstCaseSelfDamage() <= 4.0f ? 2 : 0;
        }
        return 1;
    }

    private static double effectiveLowerRate(
        DamageOpportunity opportunity,
        SelectionContext context
    ) {
        float useful = effectiveLowerBound(opportunity.targetDamage(), context);
        return useful / rateDenominator(opportunity.timing().p90Millis());
    }

    private static double effectiveExpectedRate(
        DamageOpportunity opportunity,
        SelectionContext context
    ) {
        float useful = effectiveExpected(opportunity.targetDamage(), context);
        return useful / rateDenominator(opportunity.timing().expectedMillis());
    }

    private static double rateDenominator(double millis) {
        if (!Double.isFinite(millis)) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.max(1.0, millis);
    }

    private static double safeCompletionMillis(DamageOpportunity opportunity) {
        double millis = opportunity.timing().p90Millis();
        return Double.isFinite(millis) ? millis : Double.MAX_VALUE;
    }
}
