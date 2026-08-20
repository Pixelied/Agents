package dev.adrien.crystaloptimizer.v2.reactive;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;
import dev.adrien.crystaloptimizer.client.execution.DispatchReceipt;
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

final class DeferredReactiveContinuationTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final BlockPos BASE = new BlockPos(4, 64, 7);

    @Test
    void deferredReplacementRetriesTailOnTickWithoutRepeatingSentAttack() {
        OptimizerConfigService config = OptimizerConfigService.inMemory(
            OptimizerConfig.defaults().withEnabled(true)
        );
        CombatBlackboard blackboard = new CombatBlackboard();
        blackboard.publish(new CombatBlackboardSnapshot(
            TARGET,
            0L,
            0L,
            0L,
            0L,
            Map.of(
                ApprovalSlot.RECYCLE,
                new ActionApproval(
                    91L,
                    TARGET,
                    ApprovalSlot.RECYCLE,
                    new SpawnCrystalCycle(BASE, true),
                    DamageEstimate.exact(12.0f, 0L, 0L),
                    2.0f,
                    SequenceTiming.immediate(),
                    0L,
                    0L,
                    0L,
                    0L,
                    Long.MAX_VALUE
                )
            )
        ));

        RecordingBurstSink sink = new RecordingBurstSink();
        AtomicInteger strategicTicks = new AtomicInteger();
        ClientCombatCoordinator coordinator = new ClientCombatCoordinator(
            config,
            blackboard,
            new ReactiveCombatEngine(),
            new ActionArbiter(),
            new FakeLiveView(),
            new PendingItemLedger(),
            sink,
            new ClientCombatDiagnostics(),
            strategicTicks::incrementAndGet
        );

        coordinator.onEvent(new CombatEvent.CrystalSpawned(381, BASE, System.nanoTime()));

        assertEquals(
            List.of(new AttackKnownCrystal(381), new PlaceCrystal(BASE)),
            sink.calls().getFirst()
        );

        coordinator.tick();

        assertEquals(2, sink.calls().size(), "deferred reactive work must retry on client tick");
        assertEquals(
            List.of(new PlaceCrystal(BASE)),
            sink.calls().get(1),
            "retry must resume after the already-sent attack instead of replaying the prefix"
        );
        assertEquals(0, strategicTicks.get(),
            "a continuation retry owns the tick and must not run the strategic scanner too");

        coordinator.tick();
        assertEquals(1, strategicTicks.get(),
            "normal strategic scanning resumes once the continuation completes");
    }

    private static final class RecordingBurstSink implements ReactiveBurstSink {
        private final List<List<CombatAction>> calls = new ArrayList<>();

        @Override
        public BurstReceipt dispatch(
            dev.adrien.crystaloptimizer.v2.reactive.ReactiveDecision decision,
            OptimizerConfig config
        ) {
            calls.add(List.copyOf(decision.actions()));
            if (calls.size() == 1) {
                return new BurstReceipt(
                    List.of(
                        DispatchReceipt.sent("attack"),
                        DispatchReceipt.deferred("real rotation still converging")
                    ),
                    List.of()
                );
            }
            return new BurstReceipt(
                List.of(DispatchReceipt.sent("replacement")),
                List.of()
            );
        }

        List<List<CombatAction>> calls() {
            return List.copyOf(calls);
        }
    }

    private static final class FakeLiveView implements LiveCombatView {
        @Override public long worldRevision() { return 0L; }
        @Override public long targetRevision(UUID targetId) { return 0L; }
        @Override public long inventoryRevision() { return 0L; }
        @Override public long configRevision() { return 0L; }
        @Override public boolean targetValid(UUID targetId) { return TARGET.equals(targetId); }
        @Override public boolean liveCrystal(int entityId) { return entityId == 381; }
        @Override public boolean withinEntityReach(int entityId) { return true; }
        @Override public boolean withinBlockReach(BlockPos pos) { return true; }
        @Override public boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId) {
            return BASE.equals(basePos) && brokenCrystalEntityId == 381;
        }
        @Override public int observedCount(Item item) { return item == Items.END_CRYSTAL ? 1 : 0; }
        @Override public int selectedHotbarSlot() { return 0; }
    }
}
