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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImmediateProtectionFallbackTest {
    private final SurvivalPlanner planner = new SurvivalPlanner();

    @Test
    void newlyObservedBoundedProjectileUsesEmergencyTotemFallback() {
        assertEmergencyFallback(Confidence.BOUNDED, new TickWindow(0, 1), ThreatKind.PROJECTILE);
    }

    @Test
    void exactCurrentTickFatalDamageUsesEmergencyTotemFallback() {
        assertEmergencyFallback(Confidence.EXACT, new TickWindow(0, 0), ThreatKind.OTHER);
    }

    private void assertEmergencyFallback(Confidence confidence, TickWindow impact, ThreatKind kind) {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "test:instant"
        );
        ThreatTimeline timeline = new ThreatTimeline(List.of(new ThreatEvent(
            "instant:" + confidence,
            kind,
            impact,
            damage,
            confidence,
            Optional.empty(), Optional.empty(), true, true, true, false
        )));
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND,
            0,
            true,
            true,
            1.0,
            1,
            1
        );

        SurvivalPlan plan = planner.plan(context(), timeline, List.of(protection), SafetyMode.SAFE);

        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, plan.action());
        assertTrue(plan.simulation().feasible(), plan.simulation().reason());
        assertEquals(DeadlineStatus.BEST_EFFORT, plan.simulation().deadlineStatus());
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 100, 10, new TickWindow(2, 2)),
            EngineLimits.defaults()
        );
    }
}
