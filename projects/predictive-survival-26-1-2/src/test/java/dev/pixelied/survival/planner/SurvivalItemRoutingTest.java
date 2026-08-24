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
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.ConsumableSurvivalSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalItemRoutingTest {
    @Test
    void inventoryRoutingGeneratesExactHotbarConsumableRoute() {
        ConsumableSurvivalSnapshot consumable = new ConsumableSurvivalSnapshot(
            8, true, List.of(new EffectInstanceSnapshot("minecraft:resistance", 200, 0))
        );
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 101, 1, false,
                    Optional.empty(), Optional.empty(), Optional.empty()),
                2, new InventorySlotSnapshot(2, "minecraft:potion", 222, 1, false,
                    Optional.of(consumable), Optional.empty(), Optional.empty())
            ),
            false
        );
        RescuePolicy policy = new RescuePolicy(false, false, true, false, true, true, false);

        SurvivalAction.ApplyEffects action = assertInstanceOf(
            SurvivalAction.ApplyEffects.class,
            new SurvivalCandidateGenerator().generate(context(), timeline(), inventory, menu(), policy)
                .stream()
                .filter(SurvivalAction.ApplyEffects.class::isInstance)
                .findFirst()
                .orElseThrow()
        );

        SurvivalAction.HeldItemRef ref = action.sourceItem().orElseThrow();
        SurvivalItemRoute.HotbarSelect route = assertInstanceOf(
            SurvivalItemRoute.HotbarSelect.class,
            ref.route().orElseThrow()
        );
        assertEquals(2, route.hotbarIndex());
        assertEquals("minecraft:potion", route.itemKey());
        assertEquals(222, route.componentFingerprint());
        assertEquals(SurvivalAction.Hand.MAIN_HAND, route.destinationHand());
        assertTrue(action.requiredServerTicks() >= consumable.consumeTicks() + 1,
            "route latency must be included in the conservative action deadline");
    }

    @Test
    void disablingInventoryRoutingKeepsUnheldConsumableOutOfCandidates() {
        ConsumableSurvivalSnapshot consumable = new ConsumableSurvivalSnapshot(
            8, true, List.of(new EffectInstanceSnapshot("minecraft:resistance", 200, 0))
        );
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 101, 1, false,
                    Optional.empty(), Optional.empty(), Optional.empty()),
                2, new InventorySlotSnapshot(2, "minecraft:potion", 222, 1, false,
                    Optional.of(consumable), Optional.empty(), Optional.empty())
            ),
            false
        );
        RescuePolicy policy = new RescuePolicy(false, false, true, false, false, true, false);

        assertTrue(new SurvivalCandidateGenerator().generate(context(), timeline(), inventory, menu(), policy)
            .stream().noneMatch(SurvivalAction.ApplyEffects.class::isInstance));
    }

    private static MenuSlotMap menu() {
        return new MenuSlotMap(0, 4, Map.of(0, 36, 2, 38, 10, 10, 40, 45));
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
            DamageRange.exact(10f), Set.of(), false, 1f, false, Optional.empty(), "test:incoming"
        );
        return new ThreatTimeline(List.of(new ThreatEvent(
            "incoming", ThreatKind.OTHER, new TickWindow(30, 30), damage, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, false, true, false
        )));
    }
}
