package dev.pixelied.survival.planner;

import dev.pixelied.survival.config.RescuePolicy;
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
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ContainerRoutePlanningTimingTest {
    @Test
    void mainInventoryShieldChargesSilentReconciliationBeforeUseAndWarmup() {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, slot(0, "minecraft:diamond_sword", 101, false, Optional.empty()),
                10, slot(10, "minecraft:shield", 333, false,
                    Optional.of(BlockingProfileSnapshot.fullBlock(336))),
                40, slot(40, "minecraft:air", 0, false, Optional.empty())
            ),
            false
        );
        PredictionContext context = context();
        RescuePolicy policy = new RescuePolicy(false, true, false, false, true, true, false);

        SurvivalAction.RaiseShield action = assertInstanceOf(
            SurvivalAction.RaiseShield.class,
            new SurvivalCandidateGenerator().generate(context, timeline(), inventory, menu(), policy).stream()
                .filter(SurvivalAction.RaiseShield.class::isInstance)
                .findFirst()
                .orElseThrow()
        );
        SurvivalItemRoute.ContainerSwap route = assertInstanceOf(
            SurvivalItemRoute.ContainerSwap.class,
            action.sourceItem().orElseThrow().route().orElseThrow()
        );

        assertEquals(context.timing().containerFollowupRouteTicks(), route.requiredServerTicks());
        assertEquals(context.timing().containerFollowupRouteTicks() + 5, action.requiredServerTicks());
        assertEquals(5, action.requiredUseTicks());
    }

    @Test
    void containerTotemChargesCorrectionReturnBeforePlannerSequencesNextStep() {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, slot(0, "minecraft:diamond_sword", 101, false, Optional.empty()),
                10, deathProtectionSlot(10),
                40, slot(40, "minecraft:air", 0, false, Optional.empty())
            ),
            false
        );
        PredictionContext context = context();
        RescuePolicy policy = new RescuePolicy(true, false, false, false, true, false, false);

        SurvivalAction.EquipDeathProtection action = assertInstanceOf(
            SurvivalAction.EquipDeathProtection.class,
            new SurvivalCandidateGenerator().generate(context, timeline(), inventory, menu(), policy).stream()
                .filter(SurvivalAction.EquipDeathProtection.class::isInstance)
                .findFirst()
                .orElseThrow()
        );

        assertEquals(context.timing().serverCorrectionReturnTicks(), action.requiredServerTicks());
        assertEquals(SurvivalAction.Hand.OFF_HAND, action.hand());
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        // outbound latest = 1; RTT 50ms => conservative correction return = 2 ticks;
        // a dependent follow-up packet therefore contributes 3 ticks after the first outbound window.
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 50d, 0d, new TickWindow(0, 1)), EngineLimits.defaults()
        );
    }

    private static ThreatTimeline timeline() {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false,
            Optional.of(new Vec3Snapshot(0, 0, 5)), "test:blockable"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "blockable", ThreatKind.PROJECTILE, new TickWindow(30, 30), damage, Confidence.EXACT,
            Optional.of(new Vec3Snapshot(0, 0, 5)), Optional.empty(), false, true, false, false
        )));
    }

    private static MenuSlotMap menu() {
        return new MenuSlotMap(0, 4, Map.of(0, 36, 10, 10, 40, 45));
    }

    private static InventorySlotSnapshot slot(
        int index,
        String key,
        int fingerprint,
        boolean deathProtection,
        Optional<BlockingProfileSnapshot> blockingProfile
    ) {
        return new InventorySlotSnapshot(
            index, key, fingerprint, "minecraft:air".equals(key) ? 0 : 1, deathProtection,
            Optional.empty(), Optional.empty(), blockingProfile
        );
    }

    private static InventorySlotSnapshot deathProtectionSlot(int index) {
        return new InventorySlotSnapshot(
            index, "minecraft:totem_of_undying", 444, 1, true,
            Optional.empty(), Optional.empty(), Optional.empty(),
            Optional.of(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()), false
        );
    }
}
