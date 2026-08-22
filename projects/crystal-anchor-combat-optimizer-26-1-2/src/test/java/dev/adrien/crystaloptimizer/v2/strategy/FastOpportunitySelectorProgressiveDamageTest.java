package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class FastOpportunitySelectorProgressiveDamageTest {
    @Test
    void protectedDamageIsNotSubtractedTwice() {
        DamageEstimate estimate = new DamageEstimate(
            2.0f, 2.0f, 2.0f,
            2.0f, 2.0f, 2.0f,
            12.0f, 12.0f, 12.0f,
            0.0, 0.0, 1.0,
            Set.of(),
            1L,
            1L
        );
        SelectionContext context = new SelectionContext(
            HurtThresholdEstimate.exact(10.0f),
            20.0f,
            OptimizerStrategy.LETHAL_SPEED
        );

        assertEquals(
            2.0f,
            FastOpportunitySelector.effectiveLowerBound(estimate, context),
            0.0001f
        );
    }
}
