package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftSurvivalRuntimeOpportunityContractTest {
    @Test
    void runtimeSeparatesActualAndPlanningRiskAndRegistersLiveOpportunities() throws Exception {
        String runtime = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java"
        ));

        assertTrue(runtime.contains("private final LethalOpportunityRegistry opportunityPredictors;"));
        assertTrue(runtime.contains("private final OpportunityTimelineAssembler opportunityTimelineAssembler;"));
        assertTrue(runtime.contains("import dev.pixelied.survival.threat.opportunity.CrystalOpportunityPredictor;"));
        assertTrue(runtime.contains("import dev.pixelied.survival.threat.opportunity.BedOpportunityPredictor;"));
        assertTrue(runtime.contains("import dev.pixelied.survival.threat.opportunity.RespawnAnchorOpportunityPredictor;"));
        assertTrue(runtime.contains("new CrystalOpportunityPredictor()"));
        assertTrue(runtime.contains("new BedOpportunityPredictor()"));
        assertTrue(runtime.contains("new RespawnAnchorOpportunityPredictor()"));
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
