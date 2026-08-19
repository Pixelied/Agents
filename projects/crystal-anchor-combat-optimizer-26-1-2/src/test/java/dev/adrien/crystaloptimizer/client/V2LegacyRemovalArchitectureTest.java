package dev.adrien.crystaloptimizer.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class V2LegacyRemovalArchitectureTest {
    private static final Path CLIENT = Path.of("src/client/java/dev/adrien/crystaloptimizer/client");
    private static final Path EXECUTION = Path.of("src/main/java/dev/adrien/crystaloptimizer/execution");

    @Test
    void supersededV1RuntimeAndCommitStackAreRemoved() {
        List<Path> removed = List.of(
            CLIENT.resolve("ClientCombatRuntime.java"),
            CLIENT.resolve("ClientCombatDiagnostics.java"),
            CLIENT.resolve("execution/ActionDispatcher.java"),
            EXECUTION.resolve("CombatRuntimeEngine.java"),
            EXECUTION.resolve("CommitAbortReason.java"),
            EXECUTION.resolve("CommitPhase.java"),
            EXECUTION.resolve("CommitPolicy.java"),
            EXECUTION.resolve("CommitScheduler.java"),
            EXECUTION.resolve("ExecutionFeedback.java"),
            EXECUTION.resolve("PlanExecutionController.java"),
            EXECUTION.resolve("PlanExecutionDriver.java"),
            EXECUTION.resolve("RuntimeFrame.java"),
            EXECUTION.resolve("RuntimePlanner.java")
        );
        for (Path path : removed) {
            assertFalse(Files.exists(path), () -> "superseded V1 source still exists: " + path);
        }
    }

    @Test
    void survivingClientAdaptersContainNoV1RuntimeCompatibility() throws Exception {
        String hud = Files.readString(CLIENT.resolve("OptimizerHud.java"));
        String dispatcher = Files.readString(CLIENT.resolve("execution/VanillaInteractionDispatcher.java"));
        String restocker = Files.readString(CLIENT.resolve("execution/HotbarRestocker.java"));

        assertFalse(hud.contains("ClientCombatRuntime"));
        assertFalse(dispatcher.contains("CommitScheduler"));
        assertFalse(dispatcher.contains("CommitPhase"));
        assertFalse(dispatcher.contains("implements ActionDispatcher"));
        assertFalse(restocker.contains("CommitPhase"));
    }

    @Test
    void readmeDescribesV2ReleaseAndOptionalModMenu() throws Exception {
        String readme = Files.readString(Path.of("README.md"));
        assertTrue(readme.contains("Version 0.2.0"));
        assertTrue(readme.contains("Mod Menu is optional"));
        assertTrue(readme.contains("Default strategy is Lethal Speed"));
        assertTrue(readme.contains("real server entity IDs"));
        assertFalse(readme.contains("0.1.0.jar"));
        assertFalse(readme.contains("bounded beam search rather than a greedy highest-damage loop"));
    }
}
