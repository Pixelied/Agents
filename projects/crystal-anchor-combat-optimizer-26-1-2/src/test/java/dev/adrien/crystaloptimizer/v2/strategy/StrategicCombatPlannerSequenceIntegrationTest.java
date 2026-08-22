package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.v2.state.StrategicResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StrategicCombatPlannerSequenceIntegrationTest {
    @Test
    void workerComputationAttachesBoundedPlanForPopFinisherFixture() {
        var state = V3SequencePlannerTest.popLockFixture();
        var snapshot = V3SequencePlannerTest.strategicSnapshot(state);

        StrategicResult result = new StrategicCombatPlanner().compute(
            snapshot,
            V3SequencePlannerTest.config()
        );

        assertEquals(V3SequencePlannerTest.TARGET, result.targetId());
        assertTrue(result.plannedOpportunity().isPresent());
        assertTrue(result.plannedOpportunity().orElseThrow().certifiedLethal());
    }
}
