package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class BalancedPolicyTest {
    private final SurvivalPlanner planner = new SurvivalPlanner();

    @Test
    void balancedPreservesProtectionWhenProvenNonTotemActionIsSafe() {
        PredictionContext context = context();
        ThreatTimeline timeline = lethalTimeline();
        SurvivalAction cover = new SurvivalAction.PlaceCover(
            Map.of("incoming", DamageRange.exact(2f)),
            0, true, true, 1d, 0, 1
        );
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.generic(),
            SurvivalAction.Hand.OFF_HAND,
            0, true, true, 1d, 1, 1
        );

        SurvivalPlan plan = planner.plan(context, timeline, List.of(protection, cover), SafetyMode.BALANCED);

        assertInstanceOf(SurvivalAction.PlaceCover.class, plan.action());
    }

    @Test
    void balancedFallsBackToProtectionWhenNonTotemPrerequisiteIsUnproven() {
        PredictionContext context = context();
        ThreatTimeline timeline = lethalTimeline();
        SurvivalAction unprovenCover = new SurvivalAction.PlaceCover(
            Map.of("incoming", DamageRange.exact(2f)),
            0, true, false, 1d, 0, 1
        );
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.generic(),
            SurvivalAction.Hand.OFF_HAND,
            0, true, true, 1d, 1, 1
        );

        SurvivalPlan plan = planner.plan(context, timeline, List.of(unprovenCover, protection), SafetyMode.BALANCED);

        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, plan.action());
    }

    @Test
    void balancedNeverAllowsDeliberateDamageManipulation() {
        ActionSimulation simulation = planner.simulate(
            context(),
            lethalTimeline(),
            new SurvivalAction.DeliberateDamage(0, true, true, 1d, 0, 0),
            SafetyMode.BALANCED
        );

        assertFalse(simulation.feasible());
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 50, 0, new TickWindow(0, 0)),
            EngineLimits.defaults()
        );
    }

    private static ThreatTimeline lethalTimeline() {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "test:incoming"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "incoming", ThreatKind.OTHER, new TickWindow(1, 1), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, true, true, false
        )));
    }
}
