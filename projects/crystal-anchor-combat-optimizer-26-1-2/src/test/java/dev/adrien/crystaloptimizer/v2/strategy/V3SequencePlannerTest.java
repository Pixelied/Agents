package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3SequencePlannerTest {
    static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000021");
    static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000022");

    @Test
    void defaultsStayBounded() {
        PlanningBudget budget = PlanningBudget.defaults(System.nanoTime() + 50_000_000L);
        assertEquals(3, budget.maxDepth());
        assertEquals(12, budget.beamWidth());
        assertEquals(24, budget.maxBranchesPerNode());
    }

    @Test
    void popThenFinisherSequenceBeatsOneStepChipDamage() {
        StrategicSnapshot snapshot = strategicSnapshot(popLockFixture());
        PlannedOpportunity planned = new V3SequencePlanner().plan(
            snapshot,
            TARGET,
            DamageMap.empty(TARGET, 1L, snapshot.worldRevision()),
            config(),
            new PlanningBudget(4, 24, 24, System.nanoTime() + 50_000_000L)
        );

        assertEquals(2, planned.sequence().actions().size());
        assertEquals(AttackKnownCrystal.class, planned.sequence().actions().get(0).getClass());
        assertEquals(101, ((AttackKnownCrystal) planned.sequence().actions().get(0)).entityId());
        assertEquals(DetonateAnchor.class, planned.sequence().actions().get(1).getClass());
        assertTrue(planned.certifiedLethal());
    }

    @Test
    void directCertifiedLethalBypassesSearchEvenWithExpiredSearchDeadline() {
        CombatState state = popLockFixture();
        StrategicSnapshot snapshot = strategicSnapshot(state);
        BlockPos anchor = new BlockPos(0, 64, 5);
        DamageOpportunity direct = certifiedDirectLethal(anchor, snapshot.worldRevision());
        DamageMap directMap = new DamageMap(
            TARGET,
            1L,
            snapshot.worldRevision(),
            Map.of(direct.id(), direct)
        );

        PlannedOpportunity planned = new V3SequencePlanner().plan(
            snapshot,
            TARGET,
            directMap,
            config(),
            new PlanningBudget(4, 24, 24, 1L)
        );

        assertEquals(List.of(new DetonateAnchor(anchor)), planned.sequence().actions());
        assertTrue(planned.certifiedLethal());
    }

    static StrategicSnapshot strategicSnapshot(CombatState state) {
        CombatSnapshot base = state.base();
        return new StrategicSnapshot(
            1L,
            base.worldRevision(),
            0L,
            0L,
            1L,
            SELF,
            Map.of(TARGET, 1L),
            base,
            Map.of(),
            Set.of(),
            TargetProtectionPolicyConfig.defaults(),
            TimingSnapshot.empty(1L)
        );
    }

    static CombatState popLockFixture() {
        BlockPos anchorPos = new BlockPos(0, 64, 5);
        KnownCrystal shaped = new KnownCrystal(101, new Vec3(8.5, 65.0, 1.0));
        KnownCrystal greedy = new KnownCrystal(102, new Vec3(7.5, 65.0, 1.0));
        SimCombatant self = SimCombatant.testPlayer(20.0f);
        SimCombatant target = SimCombatant.testPlayer(5.0f).withTotem(TotemState.OFFHAND);
        CombatRegion region = CombatRegion.singleBlock(anchorPos, Blocks.RESPAWN_ANCHOR.defaultBlockState());
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
            77L,
            SELF,
            region,
            Map.of(SELF, self, TARGET, target),
            List.of(shaped, greedy),
            Map.of(anchorPos, new AnchorState(1)),
            InventoryState.empty(),
            TimingState.unknown(),
            new LegalitySnapshot(new Vec3(0.5, 65.5, -8.0), 15.0, 15.0, List.of(), false),
            spatial,
            Difficulty.NORMAL
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }

    private static DamageOpportunity certifiedDirectLethal(BlockPos anchor, long revision) {
        DamageEstimate lethal = new DamageEstimate(
            20.0f, 20.0f, 20.0f,
            20.0f, 20.0f, 20.0f,
            20.0f, 20.0f, 20.0f,
            0.0,
            1.0,
            1.0,
            Set.of(),
            revision,
            revision
        );
        return new DamageOpportunity(
            "direct-lethal",
            new FixedActionSequence(List.of(new DetonateAnchor(anchor))),
            lethal,
            OpportunityIntent.LETHAL,
            new SelfDamageEstimate(0.0f, 20.0f, false),
            ResourceChain.none(),
            SequenceTiming.immediate(),
            true,
            false,
            true,
            Set.of(anchor)
        );
    }

    static OptimizerConfig config() {
        return new OptimizerConfig(
            true,
            OptimizerStrategy.LETHAL_SPEED,
            15.0,
            0.0f,
            20.0f,
            8.0f,
            true,
            true,
            false,
            RotationMode.ADAPTIVE,
            true
        );
    }
}
