package dev.adrien.crystaloptimizer.v2.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class FastOpportunitySelectorTest {
    private final FastOpportunitySelector selector = new FastOpportunitySelector();

    @Test
    void immediateUsefulDamageBeatsHigherRawDamageBehindFeedbackBoundary() {
        SelectionContext context = new SelectionContext(
            new HurtThresholdEstimate(18.0f, 19.0f, 20.0f, 0.8),
            10.0f,
            OptimizerStrategy.LETHAL_SPEED
        );
        DamageOpportunity immediate = opportunity(
            "anchor",
            29.0f,
            SequenceTiming.immediate(),
            false,
            false
        );
        DamageOpportunity delayed = opportunity(
            "respawned-crystal",
            33.0f,
            new SequenceTiming(120.0, 150.0, 1, 0.9),
            false,
            false
        );

        assertEquals(
            "anchor",
            selector.select(List.of(delayed, immediate), context).orElseThrow().id()
        );
    }

    @Test
    void weakerProtectedHitHasZeroUsefulLowerBound() {
        HurtThresholdEstimate threshold = new HurtThresholdEstimate(
            18.0f,
            18.0f,
            18.0f,
            1.0
        );
        assertEquals(
            0.0f,
            FastOpportunitySelector.usefulLowerBound(17.0f, threshold),
            1.0e-5f
        );
    }

    @Test
    void highConfidenceLethalAlwaysBeatsNonlethalPressure() {
        SelectionContext context = new SelectionContext(
            HurtThresholdEstimate.exact(0.0f),
            8.0f,
            OptimizerStrategy.LETHAL_SPEED
        );
        DamageOpportunity lethal = opportunity(
            "lethal",
            9.0f,
            new SequenceTiming(20.0, 25.0, 0, 0.95),
            true,
            false
        );
        DamageOpportunity pressure = opportunity(
            "pressure",
            30.0f,
            SequenceTiming.immediate(),
            false,
            false
        );

        assertEquals(
            "lethal",
            selector.select(List.of(pressure, lethal), context).orElseThrow().id()
        );
    }

    private static DamageOpportunity opportunity(
        String id,
        float damage,
        SequenceTiming timing,
        boolean lethal,
        boolean popsTotem
    ) {
        return new DamageOpportunity(
            id,
            new FixedActionSequence(List.of(new DetonateAnchor(BlockPos.ZERO))),
            DamageEstimate.exact(damage, 1L, 1L),
            4.0f,
            timing,
            lethal,
            popsTotem,
            false,
            Set.of()
        );
    }
}
