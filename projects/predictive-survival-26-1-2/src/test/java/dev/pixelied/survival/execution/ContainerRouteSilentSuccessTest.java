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

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContainerRouteSilentSuccessTest {
    @Test
    void knownPreSwapEvidenceWithoutCorrectionAdvancesAfterBudgetedProcessingWindow() {
        SurvivalItemRoute.ContainerSwap route = new SurvivalItemRoute.ContainerSwap(
            10, 10, 0, 0, SurvivalAction.Hand.MAIN_HAND, "minecraft:potion", 222
        );
        EffectInstanceSnapshot resistance = new EffectInstanceSnapshot("minecraft:resistance", 200, 0);
        SurvivalAction.ApplyEffects action = new SurvivalAction.ApplyEffects(
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

        NonTotemActionExecutor executor = new NonTotemActionExecutor();
        ExecutionStatus.WaitingForServer swapping = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, context(20, sword(0), potion(10)))
        );
        assertInstanceOf(ExecutionCommand.SwapMenuSlot.class, swapping.command().orElseThrow());

        ExecutionStatus.WaitingForServer tooEarly = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context(20, potion(0), sword(10)))
        );
        assertTrue(tooEarly.command().isEmpty(),
            "the follow-up use must not overtake the routed container click");

        ExecutionStatus.WaitingForServer ordered = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context(21, potion(0), sword(10)))
        );
        assertInstanceOf(ExecutionCommand.UseItem.class, ordered.command().orElseThrow(),
            "a correctly predicted click may be silent on the wire; after the already-budgeted processing window, TCP packet ordering must allow the dependent use");
    }

    private static NonTotemExecutionContext context(
        long tick,
        InventorySlotSnapshot slot0,
        InventorySlotSnapshot slot10
    ) {
        InventorySnapshot inventory = new InventorySnapshot(0, Map.of(0, slot0, 10, slot10), false);
        ServerStateEvidenceSnapshot evidence = new ServerStateEvidenceSnapshot(
            true,
            7,
            Map.of(
                0, new ServerStateEvidenceSnapshot.StackEvidence("minecraft:diamond_sword", 101, 1, 7),
                10, new ServerStateEvidenceSnapshot.StackEvidence("minecraft:potion", 222, 1, 7)
            ),
            Map.of(),
            Map.of()
        );
        ExecutionContext base = new ExecutionContext(
            inventory,
            new MenuSlotMap(0, 4, Map.of(0, 36, 10, 10, 40, 45)),
            new TimingSnapshot(tick, 50, 0, new TickWindow(tick + 1, tick + 1)),
            tick,
            false,
            null,
            0,
            true,
            evidence
        );
        return new NonTotemExecutionContext(base, player(), Set.of());
    }

    private static InventorySlotSnapshot sword(int index) {
        return new InventorySlotSnapshot(index, "minecraft:diamond_sword", 101, 1, false,
            Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static InventorySlotSnapshot potion(int index) {
        return new InventorySlotSnapshot(index, "minecraft:potion", 222, 1, false,
            Optional.empty(), Optional.empty(), Optional.empty());
    }

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 64, 0, 0.6, 65.8, 0.6),
            new Vec3Snapshot(0, 64, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }
}
