package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.inventory.SurvivalItemRoute;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NonTotemRestorationCheckpointTest {
    @Test
    void confirmedHotbarConsumableRouteExposesExactRestorationCheckpointOnce() {
        SurvivalItemRoute.HotbarSelect route = route();
        NonTotemActionExecutor executor = new NonTotemActionExecutor();
        SurvivalAction.ApplyEffects action = action(route);

        executor.begin(action, context(0, basePlayer(), 10, potionSlot()));
        executor.observe(context(2, basePlayer(), 11, potionSlot()));
        executor.observe(context(2, resistantPlayer(), 12, potionSlot()));

        RestorationCheckpoint.Hotbar checkpoint = assertInstanceOf(
            RestorationCheckpoint.Hotbar.class,
            executor.takeRestorationCheckpoint().orElseThrow()
        );
        assertEquals(0, checkpoint.originalSelectedIndex());
        assertEquals(2, checkpoint.protectionHotbarIndex());
        assertEquals("minecraft:diamond_sword", checkpoint.originalSelectedBefore().stackKey());
        assertEquals("minecraft:potion", checkpoint.protectionAfter().stackKey());
        assertEquals(222, checkpoint.protectionAfter().componentFingerprint());
        assertTrue(executor.takeRestorationCheckpoint().isEmpty());
    }

    @Test
    void consumedHotbarRouteCapturesPostActionSlotForSafeRestoration() {
        SurvivalItemRoute.HotbarSelect route = route();
        NonTotemActionExecutor executor = new NonTotemActionExecutor();
        SurvivalAction.ApplyEffects action = action(route);

        executor.begin(action, context(0, basePlayer(), 20, potionSlot()));
        executor.observe(context(2, basePlayer(), 21, potionSlot()));
        executor.observe(context(2, resistantPlayer(), 22, airSlot()));

        RestorationCheckpoint.Hotbar checkpoint = assertInstanceOf(
            RestorationCheckpoint.Hotbar.class,
            executor.takeRestorationCheckpoint().orElseThrow()
        );
        assertEquals("minecraft:air", checkpoint.protectionAfter().stackKey(),
            "a consumed rescue stack must checkpoint the authoritative post-action slot, not its pre-use contents");
        assertEquals(0, checkpoint.protectionAfter().count());
    }

    private static SurvivalItemRoute.HotbarSelect route() {
        return new SurvivalItemRoute.HotbarSelect(
            2, SurvivalAction.Hand.MAIN_HAND, "minecraft:potion", 222
        );
    }

    private static SurvivalAction.ApplyEffects action(SurvivalItemRoute route) {
        EffectInstanceSnapshot resistance = new EffectInstanceSnapshot("minecraft:resistance", 200, 0);
        return new SurvivalAction.ApplyEffects(
            StatusEffectsSnapshot.none().apply(List.of(resistance)),
            0f,
            0f,
            "minecraft:potion",
            9,
            true,
            true,
            1d,
            1,
            1,
            Optional.of(new SurvivalAction.HeldItemRef(
                SurvivalAction.Hand.MAIN_HAND, "minecraft:potion", 222, Optional.of(route)
            )),
            List.of(resistance),
            -1f
        );
    }

    private static NonTotemExecutionContext context(
        int selected,
        PlayerSnapshot player,
        long tick,
        InventorySlotSnapshot emergencySlot
    ) {
        InventorySnapshot inventory = new InventorySnapshot(
            selected,
            Map.of(
                0, new InventorySlotSnapshot(0, "minecraft:diamond_sword", 101, 1, false,
                    Optional.empty(), Optional.empty(), Optional.empty()),
                2, emergencySlot
            ),
            false
        );
        ExecutionContext base = new ExecutionContext(
            inventory,
            new MenuSlotMap(0, 4, Map.of(0, 36, 2, 38, 40, 45)),
            new TimingSnapshot(tick, 50, 0, new TickWindow(tick + 1, tick + 1)),
            tick,
            false,
            null,
            0,
            true
        );
        return new NonTotemExecutionContext(base, player, Set.of());
    }

    private static InventorySlotSnapshot potionSlot() {
        return new InventorySlotSnapshot(2, "minecraft:potion", 222, 1, false,
            Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static InventorySlotSnapshot airSlot() {
        return new InventorySlotSnapshot(2, "minecraft:air", 0, 0, false,
            Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static PlayerSnapshot basePlayer() {
        return player(StatusEffectsSnapshot.none());
    }

    private static PlayerSnapshot resistantPlayer() {
        return player(StatusEffectsSnapshot.none().apply(List.of(
            new EffectInstanceSnapshot("minecraft:resistance", 200, 0)
        )));
    }

    private static PlayerSnapshot player(StatusEffectsSnapshot effects) {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), effects, BlockingSnapshot.none(), HurtState.unknown(), DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 64, 0, 0.6, 65.8, 0.6), new Vec3Snapshot(0, 64, 0),
            new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }
}
