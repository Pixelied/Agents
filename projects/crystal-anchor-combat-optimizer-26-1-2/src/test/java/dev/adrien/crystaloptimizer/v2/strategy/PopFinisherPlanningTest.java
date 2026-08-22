package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PopFinisherPlanningTest {
    @Test
    void lowerDamagePopThatUnlocksImmediateFinisherWins() {
        var state = V3SequencePlannerTest.popLockFixture();
        var snapshot = V3SequencePlannerTest.strategicSnapshot(state);

        PlannedOpportunity planned = new V3SequencePlanner().plan(
            snapshot,
            V3SequencePlannerTest.TARGET,
            DamageMap.empty(V3SequencePlannerTest.TARGET, 1L, snapshot.worldRevision()),
            V3SequencePlannerTest.config(),
            new PlanningBudget(4, 24, 24, System.nanoTime() + 50_000_000L)
        );

        assertEquals(AttackKnownCrystal.class, planned.sequence().actions().get(0).getClass());
        assertEquals(DetonateAnchor.class, planned.sequence().actions().get(1).getClass());
        assertEquals(0, planned.hardFeedbackBoundaries());
        assertTrue(planned.certifiedLethal());
    }
}
