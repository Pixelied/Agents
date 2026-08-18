package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.Wait;
import dev.adrien.crystaloptimizer.planner.CombatPlan;
import dev.adrien.crystaloptimizer.planner.PlanScore;
import dev.adrien.crystaloptimizer.timing.PacketDependency;
import dev.adrien.crystaloptimizer.timing.PacketDependencyGraph;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommitPolicyTest {
    private final CommitPolicy policy = new CommitPolicy(0.90, 0.80, 0.85);

    @Test
    void robustLethalZeroFeedbackLineCanFreeze() {
        assertTrue(policy.shouldCommit(plan(true, 0.96, 0.91, 0.94, false)));
    }

    @Test
    void robustLethalZeroFeedbackLineWithoutATotemPopCanFreeze() {
        assertTrue(policy.shouldCommit(plan(true, 0.96, 0.0, 0.94, false)),
            "totem-denial confidence is irrelevant when the lethal simulation never triggered a totem");
    }

    @Test
    void observedTotemPopStillRequiresHighDenialConfidence() {
        assertFalse(policy.shouldCommit(plan(true, 0.96, 0.60, 0.94, false)));
    }

    @Test
    void pressureOrSetupLineStaysReplannable() {
        assertFalse(policy.shouldCommit(plan(false, 0.0, 0.0, 0.98, false)));
    }

    @Test
    void hardServerFeedbackBoundaryPreventsAtomicCommit() {
        assertFalse(policy.shouldCommit(plan(true, 0.98, 0.96, 0.96, true)));
    }

    @Test
    void lowConfidenceLethalLineStaysReplannable() {
        assertFalse(policy.shouldCommit(plan(true, 0.88, 0.90, 0.82, false)));
    }

    private static CombatPlan plan(
        boolean lethal,
        double deathProbability,
        double totemDenialProbability,
        double robustness,
        boolean hardFeedback
    ) {
        List<CombatAction> actions = List.of(new Wait(1), new Wait(1));
        PacketDependencyGraph graph = hardFeedback
            ? new PacketDependencyGraph(List.of(PacketDependency.NONE, PacketDependency.SERVER_FEEDBACK_FOR_NEW_ENTITY))
            : PacketDependencyGraph.fromActions(actions);
        return new CombatPlan(
            actions,
            new PlanScore(
                false,
                deathProbability,
                totemDenialProbability,
                lethal ? 2 : Integer.MAX_VALUE,
                lethal ? 1.0 : 0.25,
                robustness,
                graph.feedbackBoundaryCount(),
                0.08,
                0.5,
                2.0
            ),
            graph,
            lethal,
            robustness
        );
    }
}
