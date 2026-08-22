package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.candidate.CandidateFeatureEstimator;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import dev.adrien.crystaloptimizer.reconcile.PlanAssumption;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import dev.adrien.crystaloptimizer.world.WorldHypothesis;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CounterfactualPreparationTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000981");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000982");
    private static final BlockPos BASE = new BlockPos(2, 64, 2);
    private static final BlockPos BLOCKER = BASE.above();

    private final StrategicPreparationPlanner planner = new StrategicPreparationPlanner(
        new CandidateGenerator(CandidateFeatureEstimator.conservative())
    );

    @Test
    void minedBlockHypothesisUnlocksPlacementOnlyBehindAirAssumptionAndFeedbackBoundary() {
        CombatState state = state();
        assertFalse(planner.planSequences(state, config()).stream().anyMatch(CounterfactualPreparationTest::placesAtBase));

        PlanAssumption air = PlanAssumption.blockState(BLOCKER, "minecraft:air");
        WorldHypothesis hypothesis = new WorldHypothesis(
            state.geometry().withRemoved(BLOCKER),
            Set.of(air),
            TimingTransition.BLOCK_INTERACTION_TO_ACK,
            0.90
        );

        PreparationSequence sequence = planner.planSequences(state, config(), List.of(hypothesis)).stream()
            .filter(CounterfactualPreparationTest::placesAtBase)
            .findFirst()
            .orElseThrow();

        assertTrue(sequence.assumptions().contains(air));
        assertEquals(TimingTransition.BLOCK_INTERACTION_TO_ACK, sequence.feedbackBoundary());
        assertTrue(sequence.requiresFeedback());
    }

    @Test
    void counterfactualRemovalNeverMutatesAuthoritativeGeometry() {
        CombatState state = state();
        PlanAssumption air = PlanAssumption.blockState(BLOCKER, "minecraft:air");
        WorldHypothesis hypothesis = new WorldHypothesis(
            state.geometry().withRemoved(BLOCKER),
            Set.of(air),
            TimingTransition.BLOCK_INTERACTION_TO_ACK,
            0.90
        );

        assertTrue(state.geometry().getBlockState(BLOCKER).is(Blocks.STONE));
        assertTrue(hypothesis.geometry().getBlockState(BLOCKER).isAir());
    }

    private static boolean placesAtBase(PreparationSequence sequence) {
        return sequence.actions().stream().anyMatch(action ->
            action instanceof PlaceCrystal place && place.basePos().equals(BASE)
        );
    }

    private static CombatState state() {
        InventoryState inventory = inventory(
            0,
            Map.of(0, Items.DIAMOND_SWORD, 2, Items.END_CRYSTAL),
            Map.of(Items.DIAMOND_SWORD, 1, Items.END_CRYSTAL, 16)
        );
        CombatRegion region = CombatRegion.of(
            Map.of(BASE, Blocks.OBSIDIAN.defaultBlockState(), BLOCKER, Blocks.STONE.defaultBlockState()),
            Map.of()
        );
        SimCombatant self = SimCombatant.testPlayer(20.0f);
        SimCombatant target = SimCombatant.testPlayer(20.0f);
        Map<UUID, CombatantSpatialState> spatial = Map.of(
            SELF, new CombatantSpatialState(
                new Vec3(0.5, 64.0, 0.0),
                new AABB(0.2, 64.0, -0.3, 0.8, 65.8, 0.3),
                Vec3.ZERO
            ),
            TARGET, new CombatantSpatialState(
                new Vec3(0.5, 64.0, 2.5),
                new AABB(0.2, 64.0, 2.2, 0.8, 65.8, 2.8),
                Vec3.ZERO
            )
        );
        CombatSnapshot snapshot = new CombatSnapshot(
            1L,
            SELF,
            region,
            Map.of(SELF, self, TARGET, target),
            List.of(),
            Map.of(),
            inventory,
            TimingState.unknown(),
            new LegalitySnapshot(
                new Vec3(0.5, 65.5, 0.0),
                6.0,
                6.0,
                List.of(spatial.get(SELF).boundingBox(), spatial.get(TARGET).boundingBox()),
                false
            ),
            spatial,
            Difficulty.NORMAL
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }

    private static InventoryState inventory(
        int selectedSlot,
        Map<Integer, Item> hotbar,
        Map<Item, Integer> counts
    ) {
        return new InventoryState(selectedSlot, counts, hotbar, Optional.empty());
    }

    private static OptimizerConfig config() {
        return new OptimizerConfig(
            true,
            OptimizerStrategy.LETHAL_SPEED,
            12.0,
            4.0f,
            12.0f,
            8.0f,
            true,
            false,
            false,
            RotationMode.ADAPTIVE,
            true
        );
    }
}
