package dev.pixelied.survival.debug;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.execution.ExecutionStatus;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.planner.SurvivalPlan;
import dev.pixelied.survival.planner.SurvivalPlanner;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalDebugHudTest {
    @Test
    void debugOffProducesNoHudLines() {
        SurvivalConfig config = SurvivalConfig.defaults();

        assertTrue(SurvivalDebugHud.lines(config, frame(), Optional.empty(), Optional.empty()).isEmpty());
    }

    @Test
    void debugHudIncludesThreatActionAndExecutionState() {
        SurvivalConfig config = new SurvivalConfig(SafetyMode.SAFE, true, false, true, true);
        SurvivalEngine.EngineFrame frame = frame();
        SurvivalPlan plan = new SurvivalPlanner().plan(
            frame.context(),
            frame.timeline(),
            frame.candidates(),
            SafetyMode.SAFE
        );

        List<String> lines = SurvivalDebugHud.lines(
            config,
            frame,
            Optional.of(plan),
            Optional.of(new ExecutionStatus.WaitingForServer("waiting for server"))
        );

        assertTrue(lines.stream().anyMatch(line -> line.contains("incoming")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("EquipDeathProtection")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("WaitingForServer")));
        assertTrue(lines.stream().anyMatch(line -> line.contains("Hurt")));
    }

    private static SurvivalEngine.EngineFrame frame() {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        PredictionContext context = new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(10, 80, 10, new TickWindow(11, 12)),
            EngineLimits.defaults()
        );
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "test:incoming"
        );
        ThreatTimeline timeline = new ThreatTimeline(List.of(new ThreatEvent(
            "incoming", ThreatKind.OTHER, new TickWindow(3, 3), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), false, false, false, false
        )));
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND,
            0, true, true, 1d, 1, 1
        );
        return new SurvivalEngine.EngineFrame(context, timeline, List.of(protection));
    }
}
