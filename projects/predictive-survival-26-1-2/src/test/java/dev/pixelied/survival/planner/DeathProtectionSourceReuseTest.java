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
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
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

class DeathProtectionSourceReuseTest {
    @Test
    void onePhysicalProtectionStackCannotArmBothHandsInOneContingency() {
        SurvivalAction.DeathProtectionSourceRef mainSource = new SurvivalAction.DeathProtectionSourceRef(
            10,
            "minecraft:totem_of_undying",
            777,
            new DeathProtectionRoute.ContainerSwap(10, 0, DeathProtectionRoute.Destination.MAIN_HAND)
        );
        SurvivalAction.DeathProtectionSourceRef offSource = new SurvivalAction.DeathProtectionSourceRef(
            10,
            "minecraft:totem_of_undying",
            777,
            new DeathProtectionRoute.ContainerSwap(10, 40, DeathProtectionRoute.Destination.OFF_HAND)
        );
        SurvivalAction.EquipDeathProtection main = action(SurvivalAction.Hand.MAIN_HAND, mainSource);
        SurvivalAction.EquipDeathProtection off = action(SurvivalAction.Hand.OFF_HAND, offSource);

        ContingencyPlan plan = new ContingencyPlanner().plan(
            context(), timeline(), List.of(main, off), SafetyMode.SAFE, RescueProfile.SMART
        );

        assertFalse(plan.guaranteed(),
            "one exact protection stack must not be credited as two independently available hand resources");
    }

    private static SurvivalAction.EquipDeathProtection action(
        SurvivalAction.Hand hand,
        SurvivalAction.DeathProtectionSourceRef source
    ) {
        return new SurvivalAction.EquipDeathProtection(
            DeathProtectionSnapshot.ProtectionItem.vanillaTotem(),
            hand,
            1,
            true,
            true,
            1d,
            1,
            1,
            Optional.of(source)
        );
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of("max_health", "20")
        );
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)), EngineLimits.defaults()
        );
    }

    private static ThreatTimeline timeline() {
        DamageSourceSnapshot first = new DamageSourceSnapshot(
            DamageRange.exact(100f), Set.of(), false, 1f, false, Optional.empty(), "test:first"
        );
        DamageSourceSnapshot second = new DamageSourceSnapshot(
            DamageRange.exact(100f), Set.of(), false, 1f, false, Optional.empty(), "test:second"
        );
        return new ThreatTimeline(List.of(
            new ThreatEvent(
                "first", ThreatKind.OTHER, new TickWindow(5, 5), first, Confidence.EXACT,
                Optional.empty(), Optional.empty(), false, false, false, false
            ),
            new ThreatEvent(
                "second", ThreatKind.OTHER, new TickWindow(10, 10), second, Confidence.EXACT,
                Optional.empty(), Optional.empty(), false, false, false, false
            )
        ));
    }
}
