package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
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

    public static float usefulLowerBound(
        float incomingLowerBound,
        HurtThresholdEstimate threshold
    ) {
        Objects.requireNonNull(threshold, "threshold");
        return Math.max(0.0f, incomingLowerBound - threshold.upperBound());
    }

    public static float usefulExpected(
        float incomingExpected,
        HurtThresholdEstimate threshold
    ) {
        Objects.requireNonNull(threshold, "threshold");
        return Math.max(0.0f, incomingExpected - threshold.expected());
    }

    private Comparator<DamageOpportunity> comparator(SelectionContext context) {
        return Comparator
            .comparingInt((DamageOpportunity opportunity) -> priorityClass(opportunity, context))
            .thenComparingDouble(opportunity -> usefulLowerRate(opportunity, context))
            .thenComparingDouble(opportunity -> usefulExpectedRate(opportunity, context))
            .thenComparingDouble(opportunity -> opportunity.targetDamage().lowerBound())
            .thenComparingDouble(opportunity -> opportunity.targetDamage().expected())
            .thenComparingInt(opportunity -> -opportunity.timing().hardFeedbackBoundaries())
            .thenComparingDouble(opportunity -> -safeCompletionMillis(opportunity))
            .thenComparingDouble(opportunity -> -opportunity.worstCaseSelfDamage());
    }

    private int priorityClass(DamageOpportunity opportunity, SelectionContext context) {
        boolean certifiedLethal = opportunity.lethal()
            && opportunity.targetDamage().confidence() >= LETHAL_CONFIDENCE_FLOOR
            && opportunity.targetDamage().lowerBound() >= context.targetEffectiveHealth();
        if (certifiedLethal) {
            return 5;
        }
        if (opportunity.popsTotem()
            && opportunity.timing().hardFeedbackBoundaries() == 0
            && opportunity.targetDamage().confidence() >= LETHAL_CONFIDENCE_FLOOR) {
            return 4;
        }
        if (usefulLowerBound(opportunity.targetDamage().lowerBound(), context.threshold()) > 0.0f) {
            return 3;
        }
        if (context.strategy() == OptimizerStrategy.SAFE) {
            return opportunity.worstCaseSelfDamage() <= 4.0f ? 2 : 0;
        }
        return 1;
    }

    private static double usefulLowerRate(
        DamageOpportunity opportunity,
        SelectionContext context
    ) {
        float useful = usefulLowerBound(
            opportunity.targetDamage().lowerBound(),
            context.threshold()
        );
        return useful / rateDenominator(opportunity.timing().p90Millis());
    }

    private static double usefulExpectedRate(
        DamageOpportunity opportunity,
        SelectionContext context
    ) {
        float useful = usefulExpected(
            opportunity.targetDamage().expected(),
            context.threshold()
        );
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
