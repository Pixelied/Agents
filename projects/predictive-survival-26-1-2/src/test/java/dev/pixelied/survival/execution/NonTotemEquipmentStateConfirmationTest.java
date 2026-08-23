package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.ArmorPieceSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
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

class NonTotemEquipmentStateConfirmationTest {
    private static final ArmorPieceSnapshot WEAK = new ArmorPieceSnapshot(
        ArmorPieceSnapshot.Slot.CHEST, 2f, 0f, 0, 400, true
    );
    private static final ArmorPieceSnapshot STRONG = new ArmorPieceSnapshot(
        ArmorPieceSnapshot.Slot.CHEST, 8f, 3f, 4, 500, true
    );
    private static final ArmorPieceSnapshot STRONG_WITH_LIVE_DURABILITY_DRIFT = new ArmorPieceSnapshot(
        ArmorPieceSnapshot.Slot.CHEST, 8f, 3f, 4, 499, true
    );

    @Test
    void sameRegistryKeyDoesNotSkipAPlannedStrongerArmorSwap() {
        NonTotemActionExecutor executor = new NonTotemActionExecutor();
        SurvivalAction.SwapEquipment action = action();

        ExecutionStatus.WaitingForServer waiting = assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.begin(action, context(player(WEAK), 20))
        );
        assertInstanceOf(ExecutionCommand.UseItem.class, waiting.command().orElseThrow());
    }

    @Test
    void sameRegistryKeyDoesNotConfirmUntilPlannedArmorSnapshotIsObserved() {
        NonTotemActionExecutor executor = new NonTotemActionExecutor();
        SurvivalAction.SwapEquipment action = action();
        executor.begin(action, context(player(WEAK), 20));

        assertInstanceOf(
            ExecutionStatus.WaitingForServer.class,
            executor.observe(context(player(WEAK), 21))
        );
        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context(player(STRONG), 22))
        );
    }

    @Test
    void matchingPlannedArmorCapabilityConfirmsDespiteLiveDurabilityDrift() {
        NonTotemActionExecutor executor = new NonTotemActionExecutor();
        SurvivalAction.SwapEquipment action = action();
        executor.begin(action, context(player(WEAK), 20));

        assertInstanceOf(
            ExecutionStatus.Confirmed.class,
            executor.observe(context(player(STRONG_WITH_LIVE_DURABILITY_DRIFT), 21)),
            "durability can change while the action is in flight; completion identity must use the armor capability, then replan from live durability"
        );
    }

    private static SurvivalAction.SwapEquipment action() {
        SurvivalItemRoute route = new SurvivalItemRoute.AlreadyHeld(
            SurvivalAction.Hand.MAIN_HAND,
            "minecraft:netherite_chestplate",
            222
        );
        return new SurvivalAction.SwapEquipment(
            mitigation(STRONG),
            Map.of("chest", "minecraft:netherite_chestplate"),
            0,
            true,
            true,
            1d,
            0,
            1,
            Optional.of(new SurvivalAction.HeldItemRef(
                SurvivalAction.Hand.MAIN_HAND,
                "minecraft:netherite_chestplate",
                222,
                Optional.of(route)
            )),
            Optional.of(STRONG)
        );
    }

    private static NonTotemExecutionContext context(PlayerSnapshot player, long tick) {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(0, new InventorySlotSnapshot(
                0,
                "minecraft:netherite_chestplate",
                222,
                1,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty()
            )),
            false
        );
        ExecutionContext base = new ExecutionContext(
            inventory,
            new MenuSlotMap(0, 0, Map.of(0, 36, 40, 45)),
            new TimingSnapshot(tick, 50d, 0d, new TickWindow(tick + 1, tick + 1)),
            tick,
            false,
            null,
            0,
            true,
            ServerStateEvidenceSnapshot.unknown()
        );
        return new NonTotemExecutionContext(base, player, Set.of());
    }

    private static PlayerSnapshot player(ArmorPieceSnapshot chest) {
        return new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            mitigation(chest),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 64, 0, 0.6, 65.8, 0.6),
            new Vec3Snapshot(0, 64, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of(
                "mainhand", "minecraft:netherite_chestplate",
                "chest", "minecraft:netherite_chestplate"
            )
        );
    }

    private static MitigationSnapshot mitigation(ArmorPieceSnapshot chest) {
        return new MitigationSnapshot(
            chest.armor(),
            chest.toughness(),
            1f,
            chest.enchantmentProtection(),
            false,
            0,
            List.of(chest)
        );
    }
}
