package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescueProfile;
import dev.pixelied.survival.core.*;
import dev.pixelied.survival.damage.*;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.*;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class ContingencyPlannerInFlightTest {
    @Test
    void keepsPendingShieldProgressAndAddsTotemWhenMaceAppears() {
        SurvivalAction.RaiseShield shield = new SurvivalAction.RaiseShield(
            5, true, true, true, 1d, 0f, 0, 5, 0,
            Optional.of(BlockingProfileSnapshot.fullBlock(336))
        );
        SurvivalAction.EquipDeathProtection totem = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(), SurvivalAction.Hand.MAIN_HAND,
            3, true, true, 1d, 1, 2
        );

        ContingencyPlan plan = new ContingencyPlanner().planInFlight(
            context(), arrowThenMace(), List.of(shield, totem), SafetyMode.SAFE,
            RescueProfile.CONSERVATIVE_SMART, shield, 1
        );

        assertTrue(plan.guaranteed());
        assertEquals(2, plan.steps().size());
        assertEquals(shield, plan.steps().get(0).action());
        assertEquals(1, plan.steps().get(0).activationTick(),
            "already-completed shield warmup must not be charged again after the threat schedule changes");
        assertEquals(totem, plan.steps().get(1).action());
    }

    @Test
    void dropsFutureStepWhenChangedTrajectoryRemovesSecondThreat() {
        SurvivalAction.RaiseShield shield = shield();
        SurvivalAction.EquipDeathProtection totem = totem();

        ContingencyPlan plan = new ContingencyPlanner().planInFlight(
            context(), new ThreatTimeline(List.of(arrow())), List.of(shield, totem), SafetyMode.SAFE,
            RescueProfile.CONSERVATIVE_SMART, shield, 1
        );

        assertTrue(plan.guaranteed());
        assertEquals(1, plan.steps().size());
        assertEquals(shield, plan.steps().getFirst().action());
        assertEquals(1, plan.steps().getFirst().activationTick());
    }

    private static SurvivalAction.RaiseShield shield() {
        return new SurvivalAction.RaiseShield(5, true, true, true, 1d, 0f, 0, 5, 0,
            Optional.of(BlockingProfileSnapshot.fullBlock(336)));
    }

    private static SurvivalAction.EquipDeathProtection totem() {
        return new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(), SurvivalAction.Hand.MAIN_HAND,
            3, true, true, 1d, 1, 2);
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of(),
            Map.of("max_health", "20", "head_yaw", "0")
        );
        return new PredictionContext(player, WorldSnapshot.empty(),
            new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)), EngineLimits.defaults());
    }

    private static ThreatTimeline arrowThenMace() {
        return new ThreatTimeline(List.of(arrow(), mace()));
    }

    private static ThreatEvent arrow() {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(100f), EnumSet.of(DamageFlag.IS_PROJECTILE), false, 1f, false,
            Optional.of(new Vec3Snapshot(0, 0, 5)), "minecraft:arrow");
        return new ThreatEvent("arrow", ThreatKind.PROJECTILE, new TickWindow(3, 3), damage, Confidence.EXACT,
            Optional.of(new Vec3Snapshot(0, 0, 5)), Optional.empty(), false, true, false, false);
    }

    private static ThreatEvent mace() {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(220f), EnumSet.of(DamageFlag.BYPASSES_SHIELD), false, 1f, false,
            Optional.of(new Vec3Snapshot(0, 0, 5)), "minecraft:mace_smash");
        return new ThreatEvent("mace", ThreatKind.MELEE, new TickWindow(8, 8), damage, Confidence.EXACT,
            Optional.of(new Vec3Snapshot(0, 0, 5)), Optional.empty(), false, false, false, false);
    }
}
