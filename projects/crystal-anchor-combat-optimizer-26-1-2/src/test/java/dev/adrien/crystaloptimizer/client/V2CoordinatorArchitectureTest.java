package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class V2CoordinatorArchitectureTest {
    @Test
    void reactiveEventPathContainsNoPlannerOrWorldScanWork() throws IOException {
        Path path = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        );
        String source = Files.readString(path);
        int start = source.indexOf("public void onEvent(CombatEvent event)");
        int end = source.indexOf("public ClientCombatDiagnostics diagnostics()", start);
        assertTrue(start >= 0 && end > start, "coordinator must expose a bounded onEvent body");
        String hotPath = source.substring(start, end);

        assertFalse(hotPath.contains("ClientCombatSnapshotBuilder"));
        assertFalse(hotPath.contains("BeamPlanner"));
        assertFalse(hotPath.contains("CandidateGenerator"));
        assertFalse(hotPath.contains("TargetPredictor"));
        assertTrue(hotPath.contains("blackboard.snapshot()"));
        assertTrue(hotPath.contains("reactive.decide("));
        assertTrue(hotPath.contains("dispatchDecision(selected, 0, config)"));

        int dispatchStart = source.indexOf("private void dispatchDecision(");
        int dispatchEnd = source.indexOf("private void updateContinuation(", dispatchStart);
        assertTrue(dispatchStart >= 0 && dispatchEnd > dispatchStart,
            "reactive dispatch helper must stay bounded and reusable by continuation retries");
        String dispatchPath = source.substring(dispatchStart, dispatchEnd);
        assertTrue(dispatchPath.contains("arbiter.evaluate("));
        assertTrue(dispatchPath.contains("arbiter.evaluateFromContinuation("));
        assertTrue(dispatchPath.contains("burstDispatcher.dispatch("));
        assertTrue(dispatchPath.contains("burstDispatcher.dispatchFrom("));
        assertFalse(dispatchPath.contains("ClientCombatSnapshotBuilder"));
        assertFalse(dispatchPath.contains("BeamPlanner"));
        assertFalse(dispatchPath.contains("CandidateGenerator"));
        assertFalse(dispatchPath.contains("TargetPredictor"));
    }

    @Test
    void strategicWorkLivesOnlyOnTickAndTargetExactEvaluationIsBounded() throws IOException {
        String coordinator = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));
        String targets = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/TargetManager.java"
        ));

        int tick = coordinator.indexOf("public void tick()");
        int onEvent = coordinator.indexOf("public void onEvent(CombatEvent event)");
        assertTrue(tick >= 0 && onEvent > tick);
        assertTrue(coordinator.substring(tick, onEvent).contains("strategicTick.run()"));
        assertFalse(targets.contains("SHORTLIST_LIMIT = 3"));
        assertTrue(targets.contains("StrategicTargetSelector.MAX_EXACT_TARGETS"));
        assertTrue(targets.contains("isAlliedTo"));
        assertTrue(targets.contains("stickyTarget"));
    }
}
