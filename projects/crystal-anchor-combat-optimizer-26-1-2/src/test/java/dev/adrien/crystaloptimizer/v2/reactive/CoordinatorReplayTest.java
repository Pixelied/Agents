package dev.adrien.crystaloptimizer.v2.reactive;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;
import dev.adrien.crystaloptimizer.client.v2.ClientCombatCoordinator;
import dev.adrien.crystaloptimizer.client.v2.ClientCombatDiagnostics;
import dev.adrien.crystaloptimizer.client.v2.ReactiveBurstSink;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.execution.ActionArbiter;
import dev.adrien.crystaloptimizer.v2.execution.LiveCombatView;
import dev.adrien.crystaloptimizer.v2.execution.PendingItemLedger;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboard;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboardSnapshot;
import dev.adrien.crystaloptimizer.v2.state.SpawnCrystalCycle;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CoordinatorReplayTest {
    @Test
    void crystalSpawnDispatchesPreapprovedBreakReplaceWithoutStrategicRescan() {
        UUID target = UUID.randomUUID();
        BlockPos base = new BlockPos(4, 64, 4);
        CombatBlackboard blackboard = new CombatBlackboard();
        blackboard.publish(new CombatBlackboardSnapshot(
            target,
            1L,
            1L,
            1L,
            1L,
            Map.of(
                ApprovalSlot.RECYCLE,
                new ActionApproval(
                    9L,
                    target,
                    ApprovalSlot.RECYCLE,
                    new SpawnCrystalCycle(base, true),
                    DamageEstimate.exact(12.0f, 1L, 1L),
                    2.0f,
                    SequenceTiming.immediate(),
                    1L,
                    1L,
                    1L,
                    1L,
                    Long.MAX_VALUE
                )
            )
        ));

        AtomicInteger strategicScans = new AtomicInteger();
        List<Object> sent = new ArrayList<>();
        ReactiveBurstSink sink = (decision, config) -> {
            sent.addAll(decision.actions());
            return dev.adrien.crystaloptimizer.client.v2.BurstReceipt.empty();
        };

        ClientCombatCoordinator coordinator = new ClientCombatCoordinator(
            OptimizerConfigService.inMemory(OptimizerConfig.defaults().withEnabled(true)),
            blackboard,
            new ReactiveCombatEngine(),
            new ActionArbiter(),
            new FakeLiveView(target),
            new PendingItemLedger(),
            sink,
            new ClientCombatDiagnostics(),
            strategicScans::incrementAndGet
        );

        coordinator.onEvent(new CombatEvent.CrystalSpawned(381, base, 1L));

        assertEquals(0, strategicScans.get(), "reactive packet path must not invoke strategic scanning");
        assertEquals(2, sent.size());
        assertInstanceOf(AttackKnownCrystal.class, sent.get(0));
        assertEquals(381, ((AttackKnownCrystal) sent.get(0)).entityId());
        assertInstanceOf(PlaceCrystal.class, sent.get(1));
        assertEquals(base, ((PlaceCrystal) sent.get(1)).basePos());
    }

    private static final class FakeLiveView implements LiveCombatView {
        private final UUID target;

        FakeLiveView(UUID target) {
            this.target = target;
        }

        @Override public long worldRevision() { return 1L; }
        @Override public long targetRevision(UUID targetId) { return 1L; }
        @Override public long inventoryRevision() { return 1L; }
        @Override public long configRevision() { return 1L; }
        @Override public boolean targetValid(UUID targetId) { return target.equals(targetId); }
        @Override public boolean liveCrystal(int entityId) { return entityId == 381; }
        @Override public boolean withinEntityReach(int entityId) { return entityId == 381; }
        @Override public boolean withinBlockReach(BlockPos pos) { return true; }
        @Override public boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId) { return true; }
        @Override public int observedCount(Item item) { return item == Items.END_CRYSTAL ? 2 : 0; }
        @Override public int selectedHotbarSlot() { return 0; }
    }
}
