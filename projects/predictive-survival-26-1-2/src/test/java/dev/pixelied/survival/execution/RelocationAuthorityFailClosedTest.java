package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RelocationAuthorityFailClosedTest {
    @Test
    void relocateFailsClosedWhenNoServerAuthoritativeMovementExecutorExists() {
        SurvivalAction.Relocate action = new SurvivalAction.Relocate(
            new Vec3Snapshot(3d, 64d, 0d),
            Set.of("projectile"),
            1,
            true,
            true,
            1d,
            0,
            1
        );
        NonTotemActionExecutor executor = new NonTotemActionExecutor();

        ExecutionStatus.Failed failed = assertInstanceOf(
            ExecutionStatus.Failed.class,
            executor.begin(action, context())
        );

        assertTrue(failed.replanRequired());
        assertTrue(
            failed.reason().contains("server-authoritative") || failed.reason().contains("unsupported"),
            "relocation must fail closed until a server-authoritative movement route exists"
        );
    }

    private static NonTotemExecutionContext context() {
        InventorySnapshot inventory = new InventorySnapshot(
            0,
            Map.of(0, new InventorySlotSnapshot(0, "minecraft:air", 0, false)),
            false
        );
        ExecutionContext base = new ExecutionContext(
            inventory,
            new MenuSlotMap(0, 0, Map.of(0, 36, 40, 45)),
            new TimingSnapshot(50, 50, 0, new TickWindow(51, 51)),
            50,
            false,
            null,
            0,
            true
        );
        Vec3Snapshot position = new Vec3Snapshot(0d, 64d, 0d);
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
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
            new AabbSnapshot(0d, 64d, 0d, 0.6d, 65.8d, 0.6d),
            position,
            new Vec3Snapshot(0d, 0d, 0d),
            Map.of()
        );
        return new NonTotemExecutionContext(base, player, Set.of());
    }
}
