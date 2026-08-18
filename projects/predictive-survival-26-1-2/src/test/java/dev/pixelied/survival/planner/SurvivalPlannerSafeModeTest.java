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
                DeathProtectionSnapshot.ProtectionItem.generic(),
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
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)),
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
}
