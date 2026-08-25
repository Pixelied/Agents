package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftSurvivalRuntimeOpportunityContractTest {
    @Test
    void runtimeSeparatesActualAndPlanningRiskWithEmptyOpportunityRegistryByDefault() throws Exception {
        String runtime = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java"
        ));

        assertTrue(runtime.contains("private final LethalOpportunityRegistry opportunityPredictors;"));
        assertTrue(runtime.contains("private final OpportunityTimelineAssembler opportunityTimelineAssembler;"));
        assertTrue(runtime.contains("this.opportunityPredictors = new LethalOpportunityRegistry(List.of());"));
        assertTrue(runtime.contains("new PredictionContext(reactive.player(), reactive.world(), timing, limits, safetyMode)"));
        assertTrue(runtime.contains("ThreatTimeline actualTimeline = new ThreatTimeline(predicted);"));
        assertTrue(runtime.contains("List<LethalOpportunity> opportunities = opportunityPredictors.predictAll(context);"));
        assertTrue(runtime.contains(
            "opportunityTimelineAssembler.assemble(actualTimeline, opportunities, limits.maxThreats())"
        ));
        assertTrue(runtime.contains("candidateGenerator.generate(context, planningTimeline, inventory, menu, policy)"));
        assertTrue(runtime.contains(
            "new SurvivalEngine.EngineFrame(context, actualTimeline, opportunities, planningTimeline, candidates)"
        ));
    }
}
