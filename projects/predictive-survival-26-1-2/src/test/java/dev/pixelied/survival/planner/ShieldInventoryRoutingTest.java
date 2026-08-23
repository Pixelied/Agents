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
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShieldInventoryRoutingTest {
    @Test
    void inventoryRoutingGeneratesExactHotbarShieldRoute() {
        BlockingProfileSnapshot profile = BlockingProfileSnapshot.fullBlock(336);
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 101, 1, false,
                    Optional.empty(), Optional.empty(), Optional.empty()),
                2, new InventorySlotSnapshot(2, "minecraft:shield", 333, 1, false,
                    Optional.empty(), Optional.empty(), Optional.of(profile)),
                40, new InventorySlotSnapshot(40, "minecraft:air", 0, 0, false,
                    Optional.empty(), Optional.empty(), Optional.empty())
            ),
            false
        );
        RescuePolicy policy = new RescuePolicy(false, true, false, false, true, true, false);

        SurvivalAction.RaiseShield action = assertInstanceOf(
            SurvivalAction.RaiseShield.class,
            new SurvivalCandidateGenerator().generate(context(), timeline(), inventory, menu(), policy)
                .stream()
                .filter(SurvivalAction.RaiseShield.class::isInstance)
                .findFirst()
                .orElseThrow()
        );

        SurvivalAction.HeldItemRef ref = action.sourceItem().orElseThrow();
        SurvivalItemRoute.HotbarSelect route = assertInstanceOf(
            SurvivalItemRoute.HotbarSelect.class,
            ref.route().orElseThrow()
        );
        assertEquals(2, route.hotbarIndex());
        assertEquals("minecraft:shield", route.itemKey());
        assertEquals(333, route.componentFingerprint());
        assertTrue(action.requiredServerTicks() >= 6,
            "shield route latency and warmup must both be included in the deadline");
    }

    @Test
    void idleOffhandShieldDoesNotRequireMainHandTakeoverOrInventoryRouting() {
        BlockingProfileSnapshot profile = BlockingProfileSnapshot.fullBlock(336);
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 101, 1, false,
                    Optional.empty(), Optional.empty(), Optional.empty()),
                40, new InventorySlotSnapshot(40, "minecraft:shield", 444, 1, false,
                    Optional.empty(), Optional.empty(), Optional.of(profile))
            ),
            false
        );
        RescuePolicy policy = new RescuePolicy(false, true, false, false, false, false, false);

        SurvivalAction.RaiseShield action = assertInstanceOf(
            SurvivalAction.RaiseShield.class,
            new SurvivalCandidateGenerator().generate(context(), timeline(), inventory, menu(), policy)
                .stream()
                .filter(SurvivalAction.RaiseShield.class::isInstance)
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                    "an already-held offhand shield must remain usable when main-hand takeover is disabled"))
        );

        SurvivalAction.HeldItemRef ref = action.sourceItem().orElseThrow();
        assertEquals(SurvivalAction.Hand.OFF_HAND, ref.hand());
        SurvivalItemRoute.AlreadyHeld route = assertInstanceOf(
            SurvivalItemRoute.AlreadyHeld.class,
            ref.route().orElseThrow()
        );
        assertEquals(SurvivalAction.Hand.OFF_HAND, route.destinationHand());
        assertEquals("minecraft:shield", route.itemKey());
        assertEquals(444, route.componentFingerprint());
        assertEquals(5, action.requiredServerTicks());
        assertEquals(5, action.requiredUseTicks());
    }

    private static MenuSlotMap menu() {
        return new MenuSlotMap(0, 4, Map.of(0, 36, 2, 38, 40, 45));
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            5f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player, WorldSnapshot.empty(), new TimingSnapshot(0, 50, 0, new TickWindow(1, 1)), EngineLimits.defaults()
        );
    }

    private static ThreatTimeline timeline() {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(10f), Set.of(), false, 1f, false,
            Optional.of(new Vec3Snapshot(0, 0, 5)), "test:arrow"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "arrow", ThreatKind.PROJECTILE, new TickWindow(30, 30), damage, Confidence.EXACT,
            Optional.of(new Vec3Snapshot(0, 0, 5)), Optional.empty(), false, true, false, false
        )));
    }
}
