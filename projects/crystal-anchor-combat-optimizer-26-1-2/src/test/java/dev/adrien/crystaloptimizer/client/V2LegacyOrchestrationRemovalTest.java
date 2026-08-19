package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class V2LegacyOrchestrationRemovalTest {
    @Test
    void supersededV1OrchestrationIsRemovedAfterV2Cutover() throws Exception {
        for (String file : List.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/ClientCombatRuntime.java",
            "src/client/java/dev/adrien/crystaloptimizer/client/ClientCombatDiagnostics.java",
            "src/main/java/dev/adrien/crystaloptimizer/execution/CombatRuntimeEngine.java",
            "src/main/java/dev/adrien/crystaloptimizer/execution/CommitAbortReason.java",
            "src/main/java/dev/adrien/crystaloptimizer/execution/CommitPhase.java",
            "src/main/java/dev/adrien/crystaloptimizer/execution/CommitPolicy.java",
            "src/main/java/dev/adrien/crystaloptimizer/execution/CommitScheduler.java",
            "src/main/java/dev/adrien/crystaloptimizer/execution/ExecutionFeedback.java",
            "src/main/java/dev/adrien/crystaloptimizer/execution/PlanExecutionController.java",
            "src/main/java/dev/adrien/crystaloptimizer/execution/PlanExecutionDriver.java",
            "src/main/java/dev/adrien/crystaloptimizer/execution/RuntimeFrame.java",
            "src/main/java/dev/adrien/crystaloptimizer/execution/RuntimePlanner.java"
        )) {
            assertFalse(Files.exists(Path.of(file)), "obsolete V1 orchestration remains: " + file);
        }

        String hud = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/OptimizerHud.java"
        ));
        assertFalse(hud.contains("ClientCombatRuntime"));

        String dispatcher = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java"
        ));
        assertFalse(dispatcher.contains("CommitScheduler"));
        assertFalse(dispatcher.contains("CommitPhase"));

        String restocker = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/HotbarRestocker.java"
        ));
        assertFalse(restocker.contains("CommitPhase"));
    }
}
