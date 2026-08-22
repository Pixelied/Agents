package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3TargetSelectionTest {
    private static final UUID FIRST = UUID.fromString("00000000-0000-0000-0000-000000006011");
    private static final UUID SECOND = UUID.fromString("00000000-0000-0000-0000-000000006012");
    private static final UUID THIRD = UUID.fromString("00000000-0000-0000-0000-000000006013");
    private static final UUID FOURTH = UUID.fromString("00000000-0000-0000-0000-000000006014");

    @Test
    void fourthNearestCertifiedFinishCanBeatThreeCloserTargets() {
        StrategicTargetSelector selector = new StrategicTargetSelector();
        List<TargetPreScore> scores = List.of(
            score(FIRST, 4.0),
            score(SECOND, 9.0),
            score(THIRD, 16.0),
            score(FOURTH, 25.0)
        );
        Map<UUID, DamageMap> maps = Map.of(
            FIRST, map(FIRST, 4.0f, false, 30.0),
            SECOND, map(SECOND, 5.0f, false, 30.0),
            THIRD, map(THIRD, 6.0f, false, 30.0),
            FOURTH, map(FOURTH, 8.0f, true, 25.0)
        );

        StrategicTargetSelector.Selection selected = selector.selectBest(
            scores,
            null,
            Set.of(),
            maps::get
        ).orElseThrow();

        assertEquals(FOURTH, selected.targetId());
        assertEquals(4, selected.exactEvaluated());
        assertTrue(selected.damageMap().opportunities().values().stream().anyMatch(DamageOpportunity::lethal));
    }

    private static TargetPreScore score(UUID id, double distanceSquared) {
        return new TargetPreScore(id, distanceSquared, 20.0f, 127.5f, false, false);
    }

    private static DamageMap map(UUID target, float damage, boolean lethal, double p90) {
        DamageEstimate estimate = new DamageEstimate(
            damage, damage, damage,
            damage, damage, damage,
            damage, damage, damage,
            0.0, lethal ? 1.0 : 0.0, 1.0,
            Set.of(), 77L, 77L
        );
        DamageOpportunity opportunity = new DamageOpportunity(
            "fixture:" + target,
            new FixedActionSequence(List.of(new Wait(1))),
            estimate,
            lethal ? OpportunityIntent.LETHAL : OpportunityIntent.PRESSURE,
            new SelfDamageEstimate(0.0f, 20.0f, false),
            ResourceChain.none(),
            new SequenceTiming(p90, p90, 0, 1.0),
            lethal,
            false,
            false,
            Set.of()
        );
        return new DamageMap(target, 1L, 77L, Map.of(opportunity.id(), opportunity));
    }
}
