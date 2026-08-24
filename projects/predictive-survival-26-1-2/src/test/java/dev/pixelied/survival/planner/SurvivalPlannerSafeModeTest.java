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
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalPlannerSafeModeTest {
    private final SurvivalPlanner planner = new SurvivalPlanner();

    @Test
    void safeModeChoosesProtectionWhenShieldDeadlineCannotBeMet() {
        PredictionContext context = context(EngineLimits.defaults());
        ThreatTimeline timeline = lethalTimeline(3, false);
        List<SurvivalAction> candidates = List.of(
            new SurvivalAction.RaiseShield(5, true, true, true, 1.0, 1f, 0, 5, 0),
            new SurvivalAction.EquipDeathProtection(
                DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
                SurvivalAction.Hand.OFF_HAND,
                1, true, true, 1.0, 1, 1
            )
        );

        SurvivalPlan plan = planner.plan(context, timeline, candidates, SafetyMode.SAFE);

        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, plan.action());
    }

    @Test
    void harmlessEarlierThreatDoesNotStealDeadlineFromLaterLethalHit() {
        PredictionContext context = context(EngineLimits.defaults());
        ThreatTimeline timeline = new ThreatTimeline(List.of(
            threat("chip", 1, 1f, Set.of(DamageFlag.BYPASSES_COOLDOWN)),
            threat("lethal", 5, 20f, Set.of(DamageFlag.BYPASSES_COOLDOWN))
        ));
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND,
            2, true, true, 1.0, 1, 1
        );

        ActionSimulation simulation = planner.simulate(context, timeline, protection, SafetyMode.SAFE);

        assertTrue(simulation.feasible(), simulation.reason());
        assertTrue(simulation.result().survived());
    }

    @Test
    void zeroWarmupTotemStillNeedsNextServerProcessingWindow() {
        PredictionContext context = context(EngineLimits.defaults(), new TickWindow(2, 2));
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND,
            0, true, true, 1.0, 1, 1
        );

        ActionSimulation simulation = planner.simulate(context, lethalTimeline(1, false), protection, SafetyMode.SAFE);

        assertFalse(simulation.feasible());
        assertEquals("server deadline missed", simulation.reason());
    }

    @Test
    void immediatePotentialLethalThreatUsesBestEffortDeathProtectionInsteadOfNoAction() {
        PredictionContext context = context(EngineLimits.defaults(), new TickWindow(2, 2));
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "minecraft:explosion"
        );
        ThreatTimeline timeline = new ThreatTimeline(List.of(new ThreatEvent(
            "instant", ThreatKind.OTHER, new TickWindow(0, 2), damage,
            Confidence.POTENTIAL, Optional.empty(), Optional.empty(), true, true, true, false
        )));
        SurvivalAction protection = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND,
            0, true, true, 1.0, 1, 1
        );

        SurvivalPlan plan = planner.plan(context, timeline, List.of(protection), SafetyMode.SAFE);

        assertInstanceOf(SurvivalAction.EquipDeathProtection.class, plan.action());
        assertTrue(plan.simulation().feasible(), plan.simulation().reason());
        assertEquals(DeadlineStatus.BEST_EFFORT, plan.simulation().deadlineStatus());
    }

    @Test
    void zeroWarmupEquipmentSwapStillNeedsNextServerProcessingWindow() {
        PredictionContext context = context(EngineLimits.defaults(), new TickWindow(2, 2));
        SurvivalAction swap = new SurvivalAction.SwapEquipment(
            new MitigationSnapshot(20f, 8f, 1f, 0, false, 0),
            Map.of("chest", "minecraft:netherite_chestplate"),
            0,
            true,
            true,
            1.0,
            0,
            1
        );

        ActionSimulation simulation = planner.simulate(context, lethalTimeline(1, false), swap, SafetyMode.SAFE);

        assertFalse(simulation.feasible());
        assertEquals("server deadline missed", simulation.reason());
    }

    @Test
    void safeModeUsesAlreadyActiveGuaranteedBlockWithoutWastingProtection() {
        PredictionContext context = context(EngineLimits.defaults());
        ThreatTimeline timeline = lethalTimeline(3, false);
        List<SurvivalAction> candidates = List.of(
            new SurvivalAction.RaiseShield(0, true, true, true, 1.0, 1f, 5, 5, 0),
            new SurvivalAction.EquipDeathProtection(
                DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
                SurvivalAction.Hand.OFF_HAND,
                1, true, true, 1.0, 1, 1
            )
        );

        SurvivalPlan plan = planner.plan(context, timeline, candidates, SafetyMode.SAFE);

        assertInstanceOf(SurvivalAction.RaiseShield.class, plan.action());
        assertEquals(0, plan.simulation().consumableCost());
    }


    @Test
    void shieldWarmupCompletesBeforeLaterImpact() {
        PredictionContext context = context(EngineLimits.defaults());
        SurvivalAction shield = new SurvivalAction.RaiseShield(
            5, true, true, true, 1.0, 1f, 0, 5, 0
        );

        ActionSimulation simulation = planner.simulate(context, lethalTimeline(8, false), shield, SafetyMode.SAFE);

        assertTrue(simulation.feasible(), simulation.reason());
        assertTrue(simulation.result().survived(), "a shield with enough server time to warm up must be active at impact");
    }

    @Test
    void shieldCanBlockCurrentHitBeforeThatHitDisablesBlocking() {
        PredictionContext context = context(EngineLimits.defaults());
        DamageSourceSnapshot disabling = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(DamageFlag.BYPASSES_COOLDOWN), false, 1f, false,
            Optional.empty(), "test:disabling_melee", 0f, 0f, 5f
        );
        ThreatTimeline timeline = new ThreatTimeline(List.of(new ThreatEvent(
            "disable", ThreatKind.MELEE, new TickWindow(3, 3), disabling, Confidence.EXACT,
            Optional.empty(), Optional.empty(), false, true, false, true
        )));
        SurvivalAction shield = new SurvivalAction.RaiseShield(
            0, true, true, true, 1.0, 1f, 5, 5, 0
        );

        ActionSimulation simulation = planner.simulate(context, timeline, shield, SafetyMode.SAFE);

        assertTrue(simulation.feasible(), simulation.reason());
        assertTrue(simulation.result().survived(), "the disabling melee hit is blocked before the disable takes effect");
    }

    @Test
    void shieldCanSaveMixedTimelineWithoutBlockingEveryThreat() {
        PredictionContext context = context(EngineLimits.defaults());
        ThreatEvent chip = new ThreatEvent(
            "chip", ThreatKind.OTHER, new TickWindow(1, 1),
            new DamageSourceSnapshot(
                DamageRange.exact(1f), Set.of(DamageFlag.BYPASSES_SHIELD, DamageFlag.BYPASSES_COOLDOWN),
                false, 1f, false, Optional.empty(), "test:chip"
            ),
            Confidence.EXACT, Optional.empty(), Optional.empty(), true, false, true, false
        );
        ThreatEvent lethal = threat("blockable-lethal", 3, 10f, Set.of(DamageFlag.BYPASSES_COOLDOWN));
        SurvivalAction shield = new SurvivalAction.RaiseShield(
            0, true, true, true, 1.0, 1f, 5, 5, 0
        );

        ActionSimulation simulation = planner.simulate(
            context, new ThreatTimeline(List.of(chip, lethal)), shield, SafetyMode.SAFE
        );

        assertTrue(simulation.feasible(), simulation.reason());
        assertTrue(simulation.result().survived(), "taking harmless chip damage must not invalidate a shield save");
    }

    @Test
    void consumableCanCompleteAfterHarmlessEarlyHitButBeforeLethalFire() {
        PredictionContext context = context(EngineLimits.defaults());
        ThreatEvent chip = threat("chip", 1, 1f, Set.of(DamageFlag.BYPASSES_COOLDOWN));
        ThreatEvent fire = new ThreatEvent(
            "fire", ThreatKind.OTHER, new TickWindow(8, 8),
            new DamageSourceSnapshot(
                DamageRange.exact(20f), Set.of(DamageFlag.IS_FIRE, DamageFlag.BYPASSES_COOLDOWN),
                false, 1f, false, Optional.empty(), "test:fire"
            ),
            Confidence.EXACT, Optional.empty(), Optional.empty(), true, false, true, false
        );
        StatusEffectsSnapshot fireResistance = new StatusEffectsSnapshot(
            true,
            -1,
            Map.of("minecraft:fire_resistance", new EffectInstanceSnapshot("minecraft:fire_resistance", 600, 0))
        );
        SurvivalAction action = new SurvivalAction.ApplyEffects(
            fireResistance, 0f, 0f, "minecraft:potion",
            3, true, true, 1.0, 1, 1
        );

        ActionSimulation simulation = planner.simulate(
            context, new ThreatTimeline(List.of(chip, fire)), action, SafetyMode.SAFE
        );

        assertTrue(simulation.feasible(), simulation.reason());
        assertTrue(simulation.result().survived(), "an unrelated earlier chip hit must not steal the action deadline");
    }

    @Test
    void inFlightActionIsNotAssumedCompleteBeforeItsRemainingServerWork() {
        PredictionContext context = context(EngineLimits.defaults(), new TickWindow(0, 0));
        SurvivalAction shield = new SurvivalAction.RaiseShield(
            5, true, true, true, 1.0, 1f, 5, 5, 0
        );

        ActionSimulation simulation = planner.simulateInFlight(
            context, lethalTimeline(2, false), shield, SafetyMode.SAFE
        );

        assertFalse(simulation.result().survived(), "pending work must not be modeled as already completed");
    }

    @Test
    void bypassInvulnerabilityNeverScoresProtectionAsSurvival() {
        PredictionContext context = context(EngineLimits.defaults());
        ThreatTimeline timeline = lethalTimeline(3, true);
        SurvivalAction action = new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            SurvivalAction.Hand.OFF_HAND,
            1, true, true, 1.0, 1, 1
        );

        ActionSimulation simulation = planner.simulate(context, timeline, action, SafetyMode.SAFE);

        assertFalse(simulation.result().survived());
    }

    @Test
    void plannerNeverEvaluatesBeyondCandidateCap() {
        PredictionContext context = context(new EngineLimits(128, 32, 80, 128));
        ThreatTimeline timeline = lethalTimeline(20, false);
        List<SurvivalAction> candidates = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            candidates.add(new SurvivalAction.EquipDeathProtection(
                DeathProtectionSnapshot.ProtectionItem.deterministicNoOp(),
                SurvivalAction.Hand.OFF_HAND,
                0, true, true, 0.5 + i / 100.0, 1, i
            ));
        }

        SurvivalPlan plan = planner.plan(context, timeline, candidates, SafetyMode.SAFE);

        assertEquals(32, plan.evaluatedCandidateCount());
    }

    @Test
    void safeModeRejectsDeliberateDamageCandidateEvenIfMarkedReliable() {
        PredictionContext context = context(EngineLimits.defaults());
        SurvivalAction deliberate = new SurvivalAction.DeliberateDamage(
            0, true, true, 1.0, 0, 0
        );

        ActionSimulation simulation = planner.simulate(context, lethalTimeline(10, false), deliberate, SafetyMode.SAFE);

        assertFalse(simulation.feasible());
    }

    private static PredictionContext context(EngineLimits limits) {
        return context(limits, new TickWindow(1, 1));
    }

    private static PredictionContext context(EngineLimits limits, TickWindow packetWindow) {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 50, 0, packetWindow),
            limits
        );
    }

    private static ThreatTimeline lethalTimeline(long impactTick, boolean bypassInvulnerability) {
        Set<DamageFlag> flags = bypassInvulnerability
            ? Set.of(DamageFlag.BYPASSES_INVULNERABILITY)
            : Set.of();
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), flags, false, 1f, false, Optional.empty(),
            bypassInvulnerability ? "minecraft:out_of_world" : "minecraft:explosion"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "lethal", ThreatKind.OTHER, new TickWindow(impactTick, impactTick), damage,
            Confidence.EXACT, Optional.empty(), Optional.empty(), true, true, true, false
        )));
    }

    private static ThreatEvent threat(String id, long tick, float damage, Set<DamageFlag> flags) {
        return new ThreatEvent(
            id,
            ThreatKind.OTHER,
            new TickWindow(tick, tick),
            new DamageSourceSnapshot(
                DamageRange.exact(damage), flags, false, 1f, false, Optional.empty(), "minecraft:generic"
            ),
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true,
            false
        );
    }
}
