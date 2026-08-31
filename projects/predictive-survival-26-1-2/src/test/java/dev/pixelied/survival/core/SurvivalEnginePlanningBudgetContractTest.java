package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalEnginePlanningBudgetContractTest {
    @Test
    void retryLoopSharesOneContingencyEvaluationBudget() throws Exception {
        String engine = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/SurvivalEngine.java"
        ));

        assertTrue(engine.contains("contingencyPlanner.maxEvaluations()"),
            "engine tick must start from the planner's existing bounded evaluation budget");
        assertTrue(engine.contains("remainingContingencyEvaluations"),
            "retryable execution failures must share one remaining contingency budget");
        assertTrue(engine.contains("remainingContingencyEvaluations -= contingency.evaluations()"),
            "each contingency search must debit the shared per-tick budget");
        assertTrue(Pattern.compile(
            "contingencyPlanner\\.planAcrossScenarios\\([^;]*remainingContingencyEvaluations\\s*\\)",
            Pattern.DOTALL
        ).matcher(engine).find(),
            "every retry search must receive the remaining shared budget instead of a fresh planner maximum");
    }
}
