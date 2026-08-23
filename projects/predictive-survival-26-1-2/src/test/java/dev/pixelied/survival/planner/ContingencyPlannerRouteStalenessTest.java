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
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
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

class ContingencyPlannerRouteStalenessTest {
    @Test
    void hotbarSelectionCannotBeFollowedByAnInitiallyAlreadyHeldMainhandRoute() {
        SurvivalAction.ApplyEffects earlyFireResistance = fireResistanceAction(
            1,
            new SurvivalItemRoute.HotbarSelect(
                2, SurvivalAction.Hand.MAIN_HAND, "test:fire_resist", 202
            )
        );
        SurvivalAction.ApplyEffects staleHeldHeal = healAction(
            10,
            new SurvivalItemRoute.AlreadyHeld(
                SurvivalAction.Hand.MAIN_HAND, "test:initial_mainhand_heal", 101
            )
        );

        ContingencyPlan plan = new ContingencyPlanner().plan(
            context(),
            orderedThreats(),
            List.of(earlyFireResistance, staleHeldHeal),
            SafetyMode.SAFE,
            RescueProfile.SMART
        );

        assertFalse(plan.guaranteed(),
            "after selecting another hotbar stack, a route that was only AlreadyHeld in the initial main hand is stale");
    }

    @Test
    void hotbarSelectionCannotBeFollowedByAContainerRouteBoundToTheOldSelectedSlot() {
        SurvivalAction.ApplyEffects earlyFireResistance = fireResistanceAction(
            1,
            new SurvivalItemRoute.HotbarSelect(
                2, SurvivalAction.Hand.MAIN_HAND, "test:fire_resist", 202
            )
        );
        SurvivalAction.ApplyEffects staleContainerHeal = healAction(
            13,
            new SurvivalItemRoute.ContainerSwap(
                10, 10, 0, 0,
                SurvivalAction.Hand.MAIN_HAND,
                "test:container_heal",
                303,
                3
            )
        );

        ContingencyPlan plan = new ContingencyPlanner().plan(
            context(),
            orderedThreats(),
            List.of(earlyFireResistance, staleContainerHeal),
            SafetyMode.SAFE,
            RescueProfile.SMART
        );

        assertFalse(plan.guaranteed(),
            "a main-hand container route is bound to the selected hotbar slot captured when candidates were generated");
    }

    private static SurvivalAction.ApplyEffects fireResistanceAction(
        int requiredServerTicks,
        SurvivalItemRoute route
    ) {
        EffectInstanceSnapshot fireResistance = new EffectInstanceSnapshot("minecraft:fire_resistance", 200, 0);
        return new SurvivalAction.ApplyEffects(
            StatusEffectsSnapshot.none().apply(List.of(fireResistance)),
            0f,
            0f,
            route.itemKey(),
            requiredServerTicks,
            true,
            true,
            1d,
            1,
            1,
            Optional.of(new SurvivalAction.HeldItemRef(
                route.destinationHand(), route.itemKey(), route.componentFingerprint(), Optional.of(route)
            )),
            List.of(fireResistance),
            -1f
        );
    }

    private static SurvivalAction.ApplyEffects healAction(
        int requiredServerTicks,
        SurvivalItemRoute route
    ) {
        return new SurvivalAction.ApplyEffects(
            StatusEffectsSnapshot.none(),
            10f,
            0f,
            route.itemKey(),
            requiredServerTicks,
            true,
            true,
            1d,
            1,
            1,
            Optional.of(new SurvivalAction.HeldItemRef(
                route.destinationHand(), route.itemKey(), route.componentFingerprint(), Optional.of(route)
            )),
            List.of(),
            -1f
        );
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            4f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of(),
            Map.of("max_health", "20")
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 50d, 0d, new TickWindow(1, 1)),
            EngineLimits.defaults()
        );
    }

    private static ThreatTimeline orderedThreats() {
        DamageSourceSnapshot fire = new DamageSourceSnapshot(
            DamageRange.exact(100f),
            Set.of(DamageFlag.IS_FIRE),
            false,
            1f,
            false,
            Optional.empty(),
            "test:early_fire"
        );
        DamageSourceSnapshot later = new DamageSourceSnapshot(
            DamageRange.exact(10f),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "test:later_generic"
        );
        return new ThreatTimeline(List.of(
            new ThreatEvent(
                "early-fire", ThreatKind.OTHER, new TickWindow(5, 5), fire, Confidence.EXACT,
                Optional.empty(), Optional.empty(), false, false, false, false
            ),
            new ThreatEvent(
                "later-generic", ThreatKind.OTHER, new TickWindow(20, 20), later, Confidence.EXACT,
                Optional.empty(), Optional.empty(), false, false, false, false
            )
        ));
    }
}
