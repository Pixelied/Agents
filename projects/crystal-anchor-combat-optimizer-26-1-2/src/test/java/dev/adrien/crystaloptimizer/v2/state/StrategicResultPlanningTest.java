package dev.adrien.crystaloptimizer.v2.state;

import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import dev.adrien.crystaloptimizer.v2.strategy.DamageOpportunity;
import dev.adrien.crystaloptimizer.v2.strategy.OpportunityIntent;
import dev.adrien.crystaloptimizer.v2.strategy.PlannedOpportunity;
import dev.adrien.crystaloptimizer.v2.strategy.ResourceChain;
import dev.adrien.crystaloptimizer.v2.strategy.SelfDamageEstimate;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StrategicResultPlanningTest {
    @Test
    void compatibilityConstructorHasNoPlanAndExtendedResultPreservesPlan() {
        UUID target = UUID.fromString("00000000-0000-0000-0000-000000000771");
        DamageMap map = DamageMap.empty(target, 3L, 4L);
        StrategicResult legacy = new StrategicResult(1L, 4L, 5L, 6L, target, map);
        assertTrue(legacy.plannedOpportunity().isEmpty());

        BlockPos pos = new BlockPos(1, 64, 1);
        FixedActionSequence sequence = new FixedActionSequence(List.of(new DetonateAnchor(pos)));
        DamageOpportunity terminal = new DamageOpportunity(
            "planned-terminal",
            sequence,
            DamageEstimate.exact(12.0f, 4L, 3L),
            OpportunityIntent.LETHAL,
            new SelfDamageEstimate(0.0f, 20.0f, false),
            ResourceChain.none(),
            SequenceTiming.immediate(),
            true,
            false,
            true,
            Set.of(pos)
        );
        PlannedOpportunity planned = new PlannedOpportunity(sequence, terminal, 0.0, 0, true);
        StrategicResult extended = new StrategicResult(
            1L, 4L, 5L, 6L, target, map, Optional.of(planned)
        );

        assertEquals(planned, extended.plannedOpportunity().orElseThrow());
    }
}
