package dev.adrien.crystaloptimizer.v2.reactive;

import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;
import dev.adrien.crystaloptimizer.client.v2.BurstReceipt;
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
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class CoordinatorKickstartTest {
    @Test
    void strategicTickKickstartsApprovedPlaceWithoutExternalCombatEvent() {
        UUID target = UUID.randomUUID();
        BlockPos base = new BlockPos(4, 64, 4);
        CombatBlackboard blackboard = new CombatBlackboard();
        OptimizerConfigService config = OptimizerConfigService.inMemory(
            OptimizerConfig.defaults().withEnabled(true)
        );
        AtomicLong approvalId = new AtomicLong();
        Runnable strategicTick = () -> blackboard.publish(snapshot(
            approvalId.getAndIncrement(), target, base, config.revision()
        ));
        List<Object> sent = new ArrayList<>();
        ReactiveBurstSink sink = (decision, ignored) -> {
            sent.addAll(decision.actions());
            return BurstReceipt.empty();
        };

        ClientCombatCoordinator coordinator = new ClientCombatCoordinator(
            config,
            blackboard,
            new ReactiveCombatEngine(),
            new ActionArbiter(),
            new FakeLiveView(target, config.revision()),
            new PendingItemLedger(),
            sink,
            new ClientCombatDiagnostics(),
            strategicTick
        );

        coordinator.tick();

        assertEquals(1, sent.size(), "strategic approval must initiate combat without a packet wakeup");
        assertInstanceOf(PlaceCrystal.class, sent.getFirst());
        assertEquals(base, ((PlaceCrystal) sent.getFirst()).basePos());
    }

    @Test
    void repeatedStrategicScansDoNotResendSameIdleKickstart() {
        UUID target = UUID.randomUUID();
        BlockPos base = new BlockPos(4, 64, 4);
        CombatBlackboard blackboard = new CombatBlackboard();
        OptimizerConfigService config = OptimizerConfigService.inMemory(
            OptimizerConfig.defaults().withEnabled(true)
        );
        AtomicLong approvalId = new AtomicLong();
        Runnable strategicTick = () -> blackboard.publish(snapshot(
            approvalId.getAndIncrement(), target, base, config.revision()
        ));
        List<Object> sent = new ArrayList<>();
        ReactiveBurstSink sink = (decision, ignored) -> {
            sent.addAll(decision.actions());
            return BurstReceipt.empty();
        };

        ClientCombatCoordinator coordinator = new ClientCombatCoordinator(
            config,
            blackboard,
            new ReactiveCombatEngine(),
            new ActionArbiter(),
            new FakeLiveView(target, config.revision()),
            new PendingItemLedger(),
            sink,
            new ClientCombatDiagnostics(),
            strategicTick
        );

        coordinator.tick();
        coordinator.tick();
        coordinator.tick();

        assertEquals(1, sent.size(), "same world/target/inventory/config action must not spam every tick");
    }

    private static CombatBlackboardSnapshot snapshot(
        long approvalId,
        UUID target,
        BlockPos base,
        long configRevision
    ) {
        ActionApproval approval = new ActionApproval(
            approvalId,
            target,
            ApprovalSlot.PLACE,
            new FixedActionSequence(List.of(new PlaceCrystal(base))),
            DamageEstimate.exact(12.0f, 1L, 1L),
            2.0f,
            SequenceTiming.immediate(),
            1L,
            1L,
            1L,
            configRevision,
            Long.MAX_VALUE
        );
        return new CombatBlackboardSnapshot(
            target,
            1L,
            1L,
            1L,
            configRevision,
            Map.of(ApprovalSlot.PLACE, approval)
        );
    }

    private static final class FakeLiveView implements LiveCombatView {
        private final UUID target;
        private final long configRevision;

        private FakeLiveView(UUID target, long configRevision) {
            this.target = target;
            this.configRevision = configRevision;
        }

        @Override public long worldRevision() { return 1L; }
        @Override public long targetRevision(UUID targetId) { return 1L; }
        @Override public long inventoryRevision() { return 1L; }
        @Override public long configRevision() { return configRevision; }
        @Override public boolean targetValid(UUID targetId) { return target.equals(targetId); }
        @Override public boolean liveCrystal(int entityId) { return false; }
        @Override public boolean withinEntityReach(int entityId) { return false; }
        @Override public boolean withinBlockReach(BlockPos pos) { return true; }
        @Override public boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId) { return true; }
        @Override public int observedCount(Item item) { return item == Items.END_CRYSTAL ? 64 : 0; }
        @Override public int selectedHotbarSlot() { return 0; }
    }
}
