package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescueProfile;
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
import dev.pixelied.survival.damage.BlockingProfileSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContingencyPlannerTest {
    @Test
    void plansShieldThenTotemForArrowFollowedByMace() {
        PredictionContext context = context();
        ThreatTimeline timeline = arrowThenMace();
        SurvivalAction.RaiseShield shield = shield();
        SurvivalAction.EquipDeathProtection totem = totem();

        SurvivalPlanner single = new SurvivalPlanner();
        assertFalse(single.simulate(context, timeline, shield, SafetyMode.SAFE).result().survived());
        assertFalse(single.simulate(context, timeline, totem, SafetyMode.SAFE).result().survived());

        ContingencyPlan plan = new ContingencyPlanner().plan(
            context, timeline, List.of(shield, totem), SafetyMode.SAFE, RescueProfile.CONSERVATIVE_SMART
        );

        assertTrue(plan.guaranteed());
        assertFalse(plan.truncated());
        assertEquals(2, plan.steps().size());
        assertInstanceOf(SurvivalAction.RaiseShield.class, plan.steps().get(0).action());
        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, plan.steps().get(1).action());
        assertEquals(4, plan.steps().get(0).activationTick());
        assertEquals(8, plan.steps().get(1).activationTick());
        assertTrue(plan.result().survived());
    }

    @Test
    void searchLimitFailsClosedInsteadOfCallingUnsearchedSequenceSafe() {
        PredictionContext context = context();
        ContingencyPlan plan = new ContingencyPlanner(3, 1).plan(
            context,
            arrowThenMace(),
            List.of(shield(), totem()),
            SafetyMode.SAFE,
            RescueProfile.CONSERVATIVE_SMART
        );

        assertFalse(plan.guaranteed());
        assertTrue(plan.truncated());
        assertTrue(plan.reason().contains("truncated"));
    }

    private static SurvivalAction.RaiseShield shield() {
        return new SurvivalAction.RaiseShield(
            3, true, true, true, 1d, 0f, 0, 3, 0,
            Optional.of(BlockingProfileSnapshot.fullBlock(336))
        );
    }

    private static SurvivalAction.EquipDeathProtection totem() {
        return new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.MAIN_HAND,
            3,
            true,
            true,
            1d,
            1,
            2
        );
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of("max_health", "20", "head_yaw", "0")
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)),
            EngineLimits.defaults()
        );
    }

    private static ThreatTimeline arrowThenMace() {
        DamageSourceSnapshot arrow = new DamageSourceSnapshot(
            DamageRange.exact(100f),
            EnumSet.of(DamageFlag.IS_PROJECTILE),
            false,
            1f,
            false,
            Optional.of(new Vec3Snapshot(0, 0, 5)),
            "minecraft:arrow"
        );
        DamageSourceSnapshot mace = new DamageSourceSnapshot(
            DamageRange.exact(220f),
            EnumSet.of(DamageFlag.BYPASSES_SHIELD),
            false,
            1f,
            false,
            Optional.of(new Vec3Snapshot(0, 0, 5)),
            "minecraft:mace_smash"
        );
        return new ThreatTimeline(List.of(
            new ThreatEvent(
                "arrow", ThreatKind.PROJECTILE, new TickWindow(6, 6), arrow, Confidence.EXACT,
                Optional.of(new Vec3Snapshot(0, 0, 5)), Optional.empty(), false, true, false, false
            ),
            new ThreatEvent(
                "mace", ThreatKind.MELEE, new TickWindow(10, 10), mace, Confidence.EXACT,
                Optional.of(new Vec3Snapshot(0, 0, 5)), Optional.empty(), false, false, false, false
            )
        ));
    }
}
