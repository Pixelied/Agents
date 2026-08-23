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

class NonTotemInventoryRoutingTest {
    @Test
    void hotbarRouteConfirmsExactStackBeforeSendingUse() {
        SurvivalItemRoute.HotbarSelect route = new SurvivalItemRoute.HotbarSelect(
            2, SurvivalAction.Hand.MAIN_HAND, "minecraft:potion", 222
        );
        SurvivalAction.ApplyEffects action = action(route);
        NonTotemActionExecutor executor = new NonTotemActionExecutor();

        ExecutionStatus.WaitingForServer selecting = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, context(0, 4, sword(0), potion(2), basePlayer(), 10))
        );
        ExecutionCommand.SelectHotbar select = assertInstanceOf(
            ExecutionCommand.SelectHotbar.class, selecting.command().orElseThrow()
        );
        assertEquals(2, select.hotbarIndex());

        ExecutionStatus.WaitingForServer using = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context(2, 4, sword(0), potion(2), basePlayer(), 11))
        );
        ExecutionCommand.UseItem use = assertInstanceOf(ExecutionCommand.UseItem.class, using.command().orElseThrow());
        assertEquals(SurvivalAction.Hand.MAIN_HAND, use.hand());

        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context(2, 4, sword(0), potion(2), resistantPlayer(), 12))
        );
    }

    @Test
    void hotbarRouteRejectsSameItemKeyWithDifferentComponents() {
        SurvivalItemRoute.HotbarSelect route = new SurvivalItemRoute.HotbarSelect(
            2, SurvivalAction.Hand.MAIN_HAND, "minecraft:potion", 222
        );
        NonTotemActionExecutor executor = new NonTotemActionExecutor();
        SurvivalAction.ApplyEffects action = action(route);
        executor.begin(action, context(0, 4, sword(0), potion(2), basePlayer(), 10));

        InventorySlotSnapshot wrong = new InventorySlotSnapshot(
            2, "minecraft:potion", 999, 1, false,
            Optional.empty(), Optional.empty(), Optional.empty()
        );
        ExecutionStatus.Failed failed = assertInstanceOf(
            ExecutionStatus.Failed.class,
            executor.observe(context(2, 4, sword(0), wrong, basePlayer(), 11))
        );
        assertEquals(true, failed.replanRequired());
    }

    @Test
    void silentExactContainerPredictionAdvancesOnlyAfterCorrectionWindowSettles() {
        SurvivalItemRoute.ContainerSwap route = new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:potion", 222
        );
        NonTotemActionExecutor executor = new NonTotemActionExecutor();
        SurvivalAction.ApplyEffects action = action(route);

        ExecutionStatus.WaitingForServer swapping = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, containerContext(
                4,
                sword(0),
                potion(10),
                basePlayer(),
                20
            ))
        );
        assertInstanceOf(ExecutionCommand.SwapMenuSlot.class, swapping.command().orElseThrow());

        for (long tick : new long[] {21L, 22L}) {
            ExecutionStatus.WaitingForServer waiting = assertInstanceOf(
                ExecutionStatus.WaitingForServer.class,
                executor.observe(containerContext(
                    4,
                    potion(0),
                    sword(10),
                    basePlayer(),
                    tick
                ))
            );
            assertTrue(waiting.command().isEmpty(),
                "a dependent use must not be sent before the silent-click correction window settles");
        }

        ExecutionStatus.WaitingForServer settled = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(containerContext(
                4,
                potion(0),
                sword(10),
                basePlayer(),
                23
            ))
        );
        assertInstanceOf(ExecutionCommand.UseItem.class, settled.command().orElseThrow(),
            "an exact silent prediction must advance once every modeled correction could already have returned");
    }

    @Test
    void correctiveContainerRevisionFailsClosedInsteadOfWaitingForTimeout() {
        SurvivalItemRoute.ContainerSwap route = new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:potion", 222
        );
        NonTotemActionExecutor executor = new NonTotemActionExecutor();
        executor.begin(action(route), containerContext(
            4, sword(0), potion(10), basePlayer(), 20
        ));

        ExecutionStatus.Failed failed = assertInstanceOf(
            ExecutionStatus.Failed.class,
            executor.observe(containerContext(
                5,
                sword(0),
                potion(10),
                basePlayer(),
                21
            ))
        );
        assertTrue(failed.replanRequired());
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
        int stateId,
        InventorySlotSnapshot slot0,
        InventorySlotSnapshot slot2,
        PlayerSnapshot player,
        long tick
    ) {
        InventorySnapshot inventory = new InventorySnapshot(selected, Map.of(0, slot0, 2, slot2), false);
        ExecutionContext base = new ExecutionContext(
            inventory,
            new MenuSlotMap(0, stateId, Map.of(0, 36, 2, 38, 40, 45)),
            new TimingSnapshot(tick, 50, 0, new TickWindow(tick + 1, tick + 1)),
            tick,
            false,
            null,
            0,
            true,
            ServerStateEvidenceSnapshot.unknown()
        );
        return new NonTotemExecutionContext(base, player, Set.of());
    }

    private static NonTotemExecutionContext containerContext(
        int stateId,
        InventorySlotSnapshot slot0,
        InventorySlotSnapshot slot10,
        PlayerSnapshot player,
        long tick
    ) {
        InventorySnapshot inventory = new InventorySnapshot(0, Map.of(0, slot0, 10, slot10), false);
        ExecutionContext base = new ExecutionContext(
            inventory,
            new MenuSlotMap(0, stateId, Map.of(0, 36, 10, 10, 40, 45)),
            new TimingSnapshot(tick, 50, 0, new TickWindow(tick + 1, tick + 1)),
            tick,
            false,
            null,
            0,
            true,
            ServerStateEvidenceSnapshot.unknown()
        );
        return new NonTotemExecutionContext(base, player, Set.of());
    }

    private static InventorySlotSnapshot sword(int index) {
        return new InventorySlotSnapshot(index, "minecraft:diamond_sword", 101, 1, false,
            Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static InventorySlotSnapshot potion(int index) {
        return new InventorySlotSnapshot(index, "minecraft:potion", 222, 1, false,
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
