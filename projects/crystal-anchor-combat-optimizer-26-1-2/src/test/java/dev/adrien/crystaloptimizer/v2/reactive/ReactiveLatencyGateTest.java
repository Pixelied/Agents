package dev.adrien.crystaloptimizer.v2.reactive;

import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.candidate.CandidateFeatureEstimator;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.candidate.CandidatePruner;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.planner.BeamPlanner;
import dev.adrien.crystaloptimizer.planner.PlannerBudget;
import dev.adrien.crystaloptimizer.planner.RiskBudget;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.execution.ActionArbiter;
import dev.adrien.crystaloptimizer.v2.execution.LiveCombatView;
import dev.adrien.crystaloptimizer.v2.execution.PendingItemLedger;
import dev.adrien.crystaloptimizer.v2.state.ActionApproval;
import dev.adrien.crystaloptimizer.v2.state.ApprovalSlot;
import dev.adrien.crystaloptimizer.v2.state.CombatBlackboardSnapshot;
import dev.adrien.crystaloptimizer.v2.state.SpawnCrystalCycle;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ReactiveLatencyGateTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000071");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000072");

    @Test
    void preapprovedReactivePathMeetsCpuLatencyGateAndBeatsV1() {
        LatencySamples v2 = benchmarkV2SpawnBreak(2_000, 200);
        LatencySamples v1 = benchmarkEquivalentV1Decision(2_000, 200);

        assertTrue(v2.p50Millis() <= 1.0, () -> "V2 p50=" + v2.p50Millis());
        assertTrue(v2.p95Millis() <= 2.0, () -> "V2 p95=" + v2.p95Millis());
        assertTrue(
            v1.p50Nanos() / (double)Math.max(1L, v2.p50Nanos()) >= 5.0,
            () -> "V1/V2 median ratio=" + (v1.p50Nanos() / (double)Math.max(1L, v2.p50Nanos()))
        );
    }

    private static LatencySamples benchmarkV2SpawnBreak(int iterations, int warmup) {
        BlockPos base = new BlockPos(4, 64, 4);
        ActionApproval approval = new ActionApproval(
            91L,
            TARGET,
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
        );
        CombatBlackboardSnapshot snapshot = new CombatBlackboardSnapshot(
            TARGET, 1L, 1L, 1L, 1L, Map.of(ApprovalSlot.RECYCLE, approval)
        );
        ReactiveCombatEngine engine = new ReactiveCombatEngine();
        ActionArbiter arbiter = new ActionArbiter();
        PendingItemLedger pending = new PendingItemLedger();
        LiveCombatView view = new BenchmarkView();
        OptimizerConfig config = OptimizerConfig.defaults().withEnabled(true);
        CombatEvent.CrystalSpawned[] events = new CombatEvent.CrystalSpawned[iterations + warmup];
        for (int i = 0; i < events.length; i++) {
            events[i] = new CombatEvent.CrystalSpawned(10_000 + i, base, 10_000L + i);
        }

        for (int i = 0; i < warmup; i++) {
            var decision = engine.decide(events[i], snapshot, events[i].timestampNanos()).orElseThrow();
            assertTrue(arbiter.evaluate(
                decision.approval(), decision.actions(), view, pending, config, events[i].timestampNanos()
            ).allowed());
        }

        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            CombatEvent.CrystalSpawned event = events[warmup + i];
            long start = System.nanoTime();
            var decision = engine.decide(event, snapshot, event.timestampNanos()).orElseThrow();
            var result = arbiter.evaluate(
                decision.approval(), decision.actions(), view, pending, config, event.timestampNanos()
            );
            long end = System.nanoTime();
            assertTrue(result.allowed());
            samples[i] = end - start;
        }
        return LatencySamples.of(samples);
    }

    private static LatencySamples benchmarkEquivalentV1Decision(int iterations, int warmup) {
        BeamPlanner planner = new BeamPlanner(
            new CandidateGenerator(CandidateFeatureEstimator.conservative()),
            new CandidatePruner(),
            SimulationServices.defaults(),
            RiskBudget.adaptive()
        );
        CombatState state = v1Fixture();
        PlannerBudget budget = new PlannerBudget(8, 2, 50_000_000L);

        for (int i = 0; i < warmup; i++) {
            planner.plan(state, budget);
        }
        long[] samples = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            planner.plan(state, budget);
            samples[i] = System.nanoTime() - start;
        }
        return LatencySamples.of(samples);
    }

    private static CombatState v1Fixture() {
        KnownCrystal crystal = new KnownCrystal(201, new Vec3(1.5, 65.0, 1.0));
        SimCombatant self = SimCombatant.testPlayer(20.0f);
        SimCombatant target = SimCombatant.testPlayer(20.0f);
        Map<UUID, CombatantSpatialState> spatial = Map.of(
            SELF,
            new CombatantSpatialState(
                new Vec3(0.5, 64.0, -8.0),
                new AABB(0.2, 64.0, -8.3, 0.8, 65.8, -7.7),
                Vec3.ZERO
            ),
            TARGET,
            new CombatantSpatialState(
                new Vec3(0.5, 64.0, 1.0),
                new AABB(0.2, 64.0, 0.7, 0.8, 65.8, 1.3),
                Vec3.ZERO
            )
        );
        CombatSnapshot snapshot = new CombatSnapshot(
            1L,
            SELF,
            CombatRegion.empty(),
            Map.of(SELF, self, TARGET, target),
            List.of(crystal),
            Map.of(),
            InventoryState.empty(),
            TimingState.unknown(),
            new LegalitySnapshot(new Vec3(0.5, 65.5, -8.0), 15.0, 15.0, List.of(), false),
            spatial,
            Difficulty.NORMAL
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }

    private record LatencySamples(long p50Nanos, long p95Nanos) {
        static LatencySamples of(long[] raw) {
            long[] sorted = raw.clone();
            Arrays.sort(sorted);
            return new LatencySamples(
                sorted[(int)Math.floor((sorted.length - 1) * 0.50)],
                sorted[(int)Math.floor((sorted.length - 1) * 0.95)]
            );
        }

        double p50Millis() { return p50Nanos / 1_000_000.0; }
        double p95Millis() { return p95Nanos / 1_000_000.0; }
    }

    private static final class BenchmarkView implements LiveCombatView {
        @Override public long worldRevision() { return 1L; }
        @Override public long targetRevision(UUID targetId) { return 1L; }
        @Override public long inventoryRevision() { return 1L; }
        @Override public long configRevision() { return 1L; }
        @Override public boolean targetValid(UUID targetId) { return TARGET.equals(targetId); }
        @Override public boolean liveCrystal(int entityId) { return true; }
        @Override public boolean withinEntityReach(int entityId) { return true; }
        @Override public boolean withinBlockReach(BlockPos pos) { return true; }
        @Override public boolean crystalBaseCanFollowBreak(BlockPos basePos, int brokenCrystalEntityId) { return true; }
        @Override public int observedCount(Item item) { return item == Items.END_CRYSTAL ? 64 : 0; }
        @Override public int selectedHotbarSlot() { return 0; }
    }
}
