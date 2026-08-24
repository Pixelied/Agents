package dev.pixelied.survival.debug;

import dev.pixelied.survival.config.RescuePolicy;
import dev.pixelied.survival.config.RescueProfile;
import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.planner.ContingencyPlan;
import dev.pixelied.survival.planner.PlannedStep;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.TimelineResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContingencyDebugHudTest {
    @Test
    void debugHudShowsProfileAndFullContingencySequence() {
        SurvivalConfig config = new SurvivalConfig(
            SafetyMode.SAFE,
            RescueProfile.CUSTOM,
            RescuePolicy.totemAndShield(),
            true,
            false,
            false,
            true
        );
        SurvivalAction shield = new SurvivalAction.RaiseShield(
            1, true, true, true, 1d, 0f, 5, 5, 0
        );
        SurvivalAction totem = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND, 1, true, true, 1d, 1, 1
        );
        ContingencyPlan contingency = new ContingencyPlan(
            List.of(new PlannedStep(shield, 1), new PlannedStep(totem, 5)),
            new TimelineResult(List.of(), 1f, 0f, true, 1, Optional.empty()),
            true,
            8,
            false,
            "guaranteed bounded rescue sequence"
        );

        List<String> lines = SurvivalDebugHud.lines(
            config,
            frame(),
            Optional.empty(),
            Optional.of(contingency),
            Optional.empty()
        );

        assertTrue(lines.stream().anyMatch(line -> line.contains("CUSTOM")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("RaiseShield@1")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("EquipDeathProtection@5")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("guaranteed")));
    }

    private static SurvivalEngine.EngineFrame frame() {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        PredictionContext context = new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(10, 50, 0, new TickWindow(11, 11)),
            EngineLimits.defaults()
        );
        return new SurvivalEngine.EngineFrame(context, new ThreatTimeline(List.of()), List.of());
    }
}
