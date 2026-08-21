package dev.adrien.crystaloptimizer.v2.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Items;
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
    void alreadyProcessedProtectedDamageStaysUseful() {
        SelectionContext context = new SelectionContext(
            HurtThresholdEstimate.exact(18.0f),
            20.0f,
            OptimizerStrategy.LETHAL_SPEED
        );
        assertEquals(
            2.0f,
            FastOpportunitySelector.effectiveLowerBound(
                DamageEstimate.exact(2.0f, 1L, 1L),
                context
            ),
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

    @Test
    void equalDamageAndTimingPrefersCheaperResourceSpend() {
        SelectionContext context = new SelectionContext(
            HurtThresholdEstimate.exact(0.0f),
            20.0f,
            OptimizerStrategy.LETHAL_SPEED
        );
        DamageOpportunity existingBase = opportunity(
            "existing-base",
            16.0f,
            ResourceChain.of(Map.of(Items.END_CRYSTAL, 1), 1.0)
        );
        DamageOpportunity newSupport = opportunity(
            "new-support",
            16.0f,
            ResourceChain.of(Map.of(Items.OBSIDIAN, 1, Items.END_CRYSTAL, 1), 2.0)
        );

        assertEquals(
            "existing-base",
            selector.select(List.of(newSupport, existingBase), context).orElseThrow().id()
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
            estimate(damage, lethal, popsTotem),
            4.0f,
            timing,
            lethal,
            popsTotem,
            false,
            Set.of()
        );
    }

    private static DamageOpportunity opportunity(
        String id,
        float damage,
        ResourceChain resources
    ) {
        return new DamageOpportunity(
            id,
            new FixedActionSequence(List.of(new DetonateAnchor(BlockPos.ZERO))),
            estimate(damage, false, false),
            OpportunityIntent.PRESSURE,
            new SelfDamageEstimate(2.0f, 18.0f, false),
            resources,
            SequenceTiming.immediate(),
            false,
            false,
            false,
            Set.of()
        );
    }

    private static DamageEstimate estimate(float damage, boolean lethal, boolean popsTotem) {
        return new DamageEstimate(
            damage, damage, damage,
            damage, damage, damage,
            damage, damage, damage,
            popsTotem ? 1.0 : 0.0,
            lethal ? 1.0 : 0.0,
            1.0,
            Set.of(),
            1L,
            1L
        );
    }
}
