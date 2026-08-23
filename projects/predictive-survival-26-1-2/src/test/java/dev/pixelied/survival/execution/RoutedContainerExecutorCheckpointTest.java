package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingProfileSnapshot;
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

class RoutedContainerExecutorCheckpointTest {
    @Test
    void routedShieldCapturesReversibleContainerCheckpointAfterSwapConfirmation() {
        SurvivalItemRoute.ContainerSwap route = shieldRoute();
        ShieldActionExecutor executor = new ShieldActionExecutor();
        SurvivalAction.RaiseShield action = new SurvivalAction.RaiseShield(
            6, true, true, true, 1d, 0f, 0, 5, 2,
            Optional.of(BlockingProfileSnapshot.fullBlock(336)),
            Optional.of(new SurvivalAction.HeldItemRef(
                route.destinationHand(), route.itemKey(), route.componentFingerprint(), Optional.of(route)
            ))
        );

        ExecutionStatus.WaitingForServer begin = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, shieldContext(beforeShieldSwap(), 10, 100, false, 0))
        );
        assertInstanceOf(ExecutionCommand.SwapMenuSlot.class, begin.command().orElseThrow());

        ExecutionStatus.WaitingForServer routed = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(shieldContext(afterShieldSwap(), 11, 101, false, 0))
        );
        assertInstanceOf(ExecutionCommand.UseItem.class, routed.command().orElseThrow());

        RestorationCheckpoint.RoutedContainer checkpoint = assertInstanceOf(
            RestorationCheckpoint.RoutedContainer.class,
            executor.takeRestorationCheckpoint().orElseThrow()
        );
        assertEquals("minecraft:diamond_sword", checkpoint.originalDestinationBefore().stackKey());
        assertEquals("minecraft:diamond_sword", checkpoint.sourceAfter().stackKey());
        assertEquals("minecraft:shield", checkpoint.destinationAfter().stackKey());
        assertEquals(11, checkpoint.confirmedMenuStateId());
    }

    @Test
    void routedConsumableCapturesPostUseContainerDestinationState() {
        SurvivalItemRoute.ContainerSwap route = potionRoute();
        NonTotemActionExecutor executor = new NonTotemActionExecutor();
        EffectInstanceSnapshot resistance = new EffectInstanceSnapshot("minecraft:resistance", 200, 0);
        SurvivalAction.ApplyEffects action = new SurvivalAction.ApplyEffects(
            StatusEffectsSnapshot.none().apply(List.of(resistance)),
            0f, 0f, "minecraft:potion", 9, true, true, 1d, 1, 2,
            Optional.of(new SurvivalAction.HeldItemRef(
                route.destinationHand(), route.itemKey(), route.componentFingerprint(), Optional.of(route)
            )),
            List.of(resistance), -1f
        );

        ExecutionStatus.WaitingForServer begin = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, nonTotemContext(beforePotionSwap(), 10, 200, basePlayer()))
        );
        assertInstanceOf(ExecutionCommand.SwapMenuSlot.class, begin.command().orElseThrow());

        ExecutionStatus.WaitingForServer routed = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(nonTotemContext(afterPotionSwap(), 11, 201, basePlayer()))
        );
        assertInstanceOf(ExecutionCommand.UseItem.class, routed.command().orElseThrow());

        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(nonTotemContext(afterPotionConsumed(), 12, 202, resistantPlayer()))
        );
        RestorationCheckpoint.RoutedContainer checkpoint = assertInstanceOf(
            RestorationCheckpoint.RoutedContainer.class,
            executor.takeRestorationCheckpoint().orElseThrow()
        );
        assertEquals("minecraft:diamond_sword", checkpoint.originalDestinationBefore().stackKey());
        assertEquals("minecraft:diamond_sword", checkpoint.sourceAfter().stackKey());
        assertEquals("minecraft:air", checkpoint.destinationAfter().stackKey());
        assertEquals(12, checkpoint.confirmedMenuStateId());
    }

    private static SurvivalItemRoute.ContainerSwap shieldRoute() {
        return new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:shield", 333
        );
    }

    private static SurvivalItemRoute.ContainerSwap potionRoute() {
        return new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:potion", 222
        );
    }

    private static ExecutionContext shieldContext(
        InventorySnapshot inventory,
        int stateId,
        long tick,
        boolean using,
        int useTicks
    ) {
        return new ExecutionContext(
            inventory,
            menu(stateId),
            new TimingSnapshot(tick, 50d, 0d, new TickWindow(tick + 1, tick + 1)),
            tick, using, using ? SurvivalAction.Hand.MAIN_HAND : null, useTicks, true
        );
    }

    private static NonTotemExecutionContext nonTotemContext(
        InventorySnapshot inventory,
        int stateId,
        long tick,
        PlayerSnapshot player
    ) {
        return new NonTotemExecutionContext(
            new ExecutionContext(
                inventory, menu(stateId),
                new TimingSnapshot(tick, 50d, 0d, new TickWindow(tick + 1, tick + 1)),
                tick, false, null, 0, true
            ),
            player,
            Set.of()
        );
    }

    private static MenuSlotMap menu(int stateId) {
        return new MenuSlotMap(7, stateId, Map.of(0, 36, 10, 10, 40, 45));
    }

    private static InventorySnapshot beforeShieldSwap() {
        return inventory(
            slot(0, "minecraft:diamond_sword", 101, 1, Optional.empty()),
            slot(10, "minecraft:shield", 333, 1, Optional.of(BlockingProfileSnapshot.fullBlock(336)))
        );
    }

    private static InventorySnapshot afterShieldSwap() {
        return inventory(
            slot(0, "minecraft:shield", 333, 1, Optional.of(BlockingProfileSnapshot.fullBlock(336))),
            slot(10, "minecraft:diamond_sword", 101, 1, Optional.empty())
        );
    }

    private static InventorySnapshot beforePotionSwap() {
        return inventory(
            slot(0, "minecraft:diamond_sword", 101, 1, Optional.empty()),
            slot(10, "minecraft:potion", 222, 1, Optional.empty())
        );
    }

    private static InventorySnapshot afterPotionSwap() {
        return inventory(
            slot(0, "minecraft:potion", 222, 1, Optional.empty()),
            slot(10, "minecraft:diamond_sword", 101, 1, Optional.empty())
        );
    }

    private static InventorySnapshot afterPotionConsumed() {
        return inventory(
            slot(0, "minecraft:air", 0, 0, Optional.empty()),
            slot(10, "minecraft:diamond_sword", 101, 1, Optional.empty())
        );
    }

    private static InventorySnapshot inventory(InventorySlotSnapshot destination, InventorySlotSnapshot source) {
        return new InventorySnapshot(
            0,
            Map.of(
                0, destination,
                10, source,
                40, slot(40, "minecraft:air", 0, 0, Optional.empty())
            ),
            false
        );
    }

    private static InventorySlotSnapshot slot(
        int index,
        String key,
        int fingerprint,
        int count,
        Optional<BlockingProfileSnapshot> blocking
    ) {
        return new InventorySlotSnapshot(
            index, key, fingerprint, count, false,
            Optional.empty(), Optional.empty(), blocking
        );
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
            MitigationSnapshot.none(), effects, BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 64, 0, 0.6, 65.8, 0.6),
            new Vec3Snapshot(0, 64, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }
}
