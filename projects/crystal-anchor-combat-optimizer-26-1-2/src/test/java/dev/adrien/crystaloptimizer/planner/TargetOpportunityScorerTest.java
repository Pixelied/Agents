package dev.adrien.crystaloptimizer.planner;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.timing.PacketDependencyGraph;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetOpportunityScorerTest {
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-000000001002");

    @Test
    void plannerProvenLethalOpportunityRanksAboveStrongNonLethalPressure() {
        TargetPriority lethal = TargetOpportunityScorer.priority(
            A,
            plan(score(false, 0.93, 0.0, 2, 1.0, 0.94, 0, 0.12, 0.3), true, 0.94),
            0.10,
            6.0
        );
        TargetPriority pressure = TargetOpportunityScorer.priority(
            B,
            plan(score(false, 0.0, 0.0, Integer.MAX_VALUE, 0.90, 0.99, 0, 0.02, 2.0), false, 0.99),
            0.90,
            3.0
        );

        assertTrue(lethal.killOpportunity() > pressure.killOpportunity());
    }

    @Test
    void saferFasterLowerDependencyLethalLineGetsHigherKillOpportunity() {
        TargetPriority fast = TargetOpportunityScorer.priority(
            A,
            plan(score(false, 0.95, 0.92, 2, 1.0, 0.96, 0, 0.08, 0.5), true, 0.96),
            0.20,
            5.0
        );
        TargetPriority slow = TargetOpportunityScorer.priority(
            B,
            plan(score(false, 0.95, 0.92, 6, 1.0, 0.88, 2, 0.35, 0.5), true, 0.88),
            0.20,
            5.0
        );

        assertTrue(fast.killOpportunity() > slow.killOpportunity());
    }

    @Test
    void unacceptableSelfDeathCannotBecomeAnAttractiveTarget() {
        TargetPriority suicidal = TargetOpportunityScorer.priority(
            A,
            plan(score(true, 1.0, 1.0, 1, 1.0, 1.0, 0, 1.0, 5.0), true, 1.0),
            1.0,
            1.0
        );

        assertEquals(0.0, suicidal.killOpportunity());
    }

    @Test
    void threatAndDistanceRemainSeparateHybridSignals() {
        TargetPriority priority = TargetOpportunityScorer.priority(
            A,
            plan(score(false, 0.0, 0.0, Integer.MAX_VALUE, 0.40, 0.90, 1, 0.10, 0.6), false, 0.90),
            0.75,
            7.5
        );

        assertEquals(0.75, priority.threatScore());
        assertEquals(7.5, priority.distance());
    }

    private static CombatPlan plan(PlanScore score, boolean lethal, double robustness) {
        List<CombatAction> actions = List.of(new Wait(1));
        return new CombatPlan(
            actions,
            score,
            PacketDependencyGraph.fromActions(actions),
            lethal,
            robustness
        );
    }

    private static PlanScore score(
        boolean unacceptableSelfDeath,
        double deathProbability,
        double totemDenial,
        int ticksToKill,
        double threatNeutralization,
        double robustness,
        int feedbackBoundaries,
        double selfRisk,
        double futureGeometry
    ) {
        return new PlanScore(
            unacceptableSelfDeath,
            deathProbability,
            totemDenial,
            ticksToKill,
            threatNeutralization,
            robustness,
            feedbackBoundaries,
            selfRisk,
            futureGeometry,
            1.0
        );
    }
}
