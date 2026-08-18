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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonTotemActionTest {
    private final SurvivalPlanner planner = new SurvivalPlanner();

    @Test
    void coverMustReduceWorstCaseAndMeetDeadline() {
        PredictionContext context = context(6f);
        ThreatTimeline timeline = timeline(event("crystal", 5, 10f, Set.of(DamageFlag.IS_EXPLOSION)));
        SurvivalAction cover = new SurvivalAction.PlaceCover(
            Map.of("crystal", DamageRange.exact(3f)),
            1, true, true, 1d, 0, 1
        );

        SurvivalPlan plan = planner.plan(context, timeline, List.of(cover), SafetyMode.SAFE);

        assertInstanceOf(SurvivalAction.PlaceCover.class, plan.action());
        assertTrue(plan.simulation().result().survived());
        assertEquals(3f, plan.simulation().result().eventResult("crystal").preMitigationRaw(), 0.0001f);
    }

    @Test
    void chestplateSwapCanMakeExplosionSurvivable() {
        PredictionContext context = context(10f);
        ThreatTimeline timeline = timeline(event("blast", 5, 15f, Set.of(DamageFlag.IS_EXPLOSION)));
        MitigationSnapshot armored = new MitigationSnapshot(20f, 8f, 1f, 0, false, 0);
        SurvivalAction swap = new SurvivalAction.SwapEquipment(
            armored,
            Map.of("chest", "minecraft:netherite_chestplate"),
            1, true, true, 1d, 0, 2
        );

        ActionSimulation simulation = planner.simulate(context, timeline, swap, SafetyMode.SAFE);

        assertTrue(simulation.feasible());
        assertTrue(simulation.result().survived());
        assertTrue(simulation.result().finalHealth() > 0f);
    }

    @Test
    void thirtyTwoTickFoodUseIsRejectedForThreeTickThreat() {
        PredictionContext context = context(5f);
        ThreatTimeline timeline = timeline(event("blast", 3, 10f, Set.of(DamageFlag.IS_EXPLOSION)));
        SurvivalAction food = new SurvivalAction.ApplyEffects(
            StatusEffectsSnapshot.none(),
            4f,
            4f,
            32, true, true, 1d, 1, 1
        );

        ActionSimulation simulation = planner.simulate(context, timeline, food, SafetyMode.SAFE);

        assertFalse(simulation.feasible());
        assertEquals("server deadline missed", simulation.reason());
    }

    @Test
    void pearlRescueIncludesFiveRawPearlDamage() {
        PredictionContext context = context(10f);
        ThreatTimeline timeline = timeline(event("fall", 6, 30f, Set.of(DamageFlag.IS_FALL)));
        SurvivalAction pearl = new SurvivalAction.PearlRescue(
            Set.of("fall"),
            5,
            1, true, true, 0.95d, 1, 3
        );

        ActionSimulation simulation = planner.simulate(context, timeline, pearl, SafetyMode.SAFE);

        assertTrue(simulation.feasible());
        assertTrue(simulation.result().survived());
        assertEquals(5f, simulation.result().eventResult("ender_pearl").preMitigationRaw(), 0.0001f);
    }

    private static PredictionContext context(float health) {
        PlayerSnapshot player = new PlayerSnapshot(
            health, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)),
            EngineLimits.defaults()
        );
    }

    private static ThreatTimeline timeline(ThreatEvent event) {
        return new ThreatTimeline(List.of(event));
    }

    private static ThreatEvent event(String id, long impactTick, float raw, Set<DamageFlag> flags) {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(raw), flags, false, 1f, false, Optional.empty(),
            flags.contains(DamageFlag.IS_FALL) ? "minecraft:fall" : "minecraft:explosion"
        );
        return new ThreatEvent(
            id, ThreatKind.OTHER, new TickWindow(impactTick, impactTick), damage,
            Confidence.EXACT, Optional.empty(), Optional.empty(), true, true, true, false
        );
    }
}
