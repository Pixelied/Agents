package dev.adrien.crystaloptimizer.v2.reactive;

import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;
import dev.adrien.crystaloptimizer.client.v2.ClientCombatCoordinator;
import dev.adrien.crystaloptimizer.client.v2.ClientCombatDiagnostics;
import dev.adrien.crystaloptimizer.client.v2.BurstReceipt;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.execution.ActionArbiter;
import dev.adrien.crystaloptimizer.v2.execution.LiveCombatView;
import dev.adrien.crystaloptimizer.v2.execution.PendingItemLedger;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboard;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CoordinatorPendingItemLifecycleTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000701");

    @Test
    void visibleConsumptionReleasesSuccessfulPlacementReservation() {
        PendingItemLedger pending = new PendingItemLedger();
        pending.reserve(500L, Items.END_CRYSTAL, 1, 1);
        FakeLiveView view = new FakeLiveView(0);
        ClientCombatCoordinator coordinator = coordinator(view, pending);

        coordinator.onEvent(new CombatEvent.BlockChanged(new BlockPos(0, 64, 0), System.nanoTime()));

        assertEquals(0, pending.reservationCount(),
            "a visible item-count drop must reconcile the predicted spend");
    }

    @Test
    void unchangedCountStaysReservedBrieflyButCannotLeakForever() {
        PendingItemLedger pending = new PendingItemLedger();
        pending.reserve(501L, Items.END_CRYSTAL, 1, 1);
        FakeLiveView view = new FakeLiveView(1);
        ClientCombatCoordinator coordinator = coordinator(view, pending);
        long now = System.nanoTime();

        coordinator.onEvent(new CombatEvent.BlockChanged(new BlockPos(0, 64, 0), now));
        assertEquals(1, pending.reservationCount(),
            "an immediate unrelated event must not release an unconfirmed spend");

        coordinator.onEvent(new CombatEvent.BlockChanged(
            new BlockPos(1, 64, 0),
            now + 10_000_000_000L
        ));
        assertEquals(0, pending.reservationCount(),
            "stale reservations need a bounded reconciliation timeout");
    }

    private static ClientCombatCoordinator coordinator(
        LiveCombatView view,
        PendingItemLedger pending
    ) {
        return new ClientCombatCoordinator(
            OptimizerConfigService.inMemory(OptimizerConfig.defaults().withEnabled(true)),
            new CombatBlackboard(),
            new ReactiveCombatEngine(),
            new ActionArbiter(),
            view,
            pending,
            (decision, config) -> BurstReceipt.empty(),
            new ClientCombatDiagnostics(),
            () -> {}
        );
    }

    private static final class FakeLiveView implements LiveCombatView {
        private final int crystals;

        FakeLiveView(int crystals) {
            this.crystals = crystals;
        }

        @Override public long worldRevision() { return 0L; }
        @Override public long targetRevision(UUID targetId) { return 0L; }
        @Override public long inventoryRevision() { return 0L; }
        @Override public long configRevision() { return 0L; }
        @Override public boolean targetValid(UUID targetId) { return TARGET.equals(targetId); }
        @Override public boolean liveCrystal(int entityId) { return false; }
        @Override public boolean withinEntityReach(int entityId) { return false; }
        @Override public boolean withinBlockReach(BlockPos pos) { return true; }
        @Override public boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId) { return false; }
        @Override public int observedCount(Item item) { return item == Items.END_CRYSTAL ? crystals : 0; }
        @Override public int selectedHotbarSlot() { return 0; }
    }
}
