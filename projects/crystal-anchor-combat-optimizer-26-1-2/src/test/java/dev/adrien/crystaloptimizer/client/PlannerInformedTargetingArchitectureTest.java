package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerInformedTargetingArchitectureTest {
    @Test
    void liveTargetSelectionUsesBoundedPlannerEvidenceAndReusesSelectedSnapshot() throws IOException {
        Path runtimePath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/ClientCombatRuntime.java"
        );
        String source = Files.readString(runtimePath);

        assertTrue(source.contains("TARGET_SHORTLIST_LIMIT"),
            "planner-informed targeting must cap the visible shortlist");
        assertTrue(source.contains("TARGET_REEVALUATION_INTERVAL_TICKS"),
            "expensive challenger evaluation must be rate limited");
        assertTrue(source.contains("targetSelectionBudget"),
            "target pre-pass must have a smaller dedicated planner budget");
        assertTrue(source.contains(".limit(TARGET_SHORTLIST_LIMIT)"),
            "only the bounded shortlist may receive shallow beam plans");
        assertTrue(source.contains("snapshotBuilder.build(candidate)"),
            "each shortlisted candidate must be evaluated from a real immutable snapshot");
        assertTrue(source.contains("beamPlanner.plan("));
        assertTrue(source.contains("TargetOpportunityScorer.priority("),
            "final kill opportunity must come from planner evidence, not visible-health heuristics");
        assertTrue(source.contains("targetSelector.select(previousTarget, priorities, riskBudget)"),
            "planner opportunity must still feed the existing hybrid threat/hysteresis selector");
        assertTrue(source.contains("targetReevaluationTicks"));
        assertTrue(source.contains("record TargetSelection"));
        assertTrue(source.contains("currentSnapshot = selection.snapshot()"),
            "the selected pre-pass snapshot must be reused for the full plan instead of scanning twice");
    }
}
