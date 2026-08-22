package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.candidate.CandidateFeatureEstimator;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

final class OffhandPreparationPlannerTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000c01");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000c02");
    private static final BlockPos SUPPORT = new BlockPos(1, 63, 2);

    @Test
    void coldCrystalSetupUsesOffhandCrystalWithoutPointlessHotbarSwap() {
        InventoryState inventory = new InventoryState(
            1,
            Map.of(Items.OBSIDIAN, 16, Items.END_CRYSTAL, 16),
            Map.of(1, Items.OBSIDIAN),
            Map.of(1, 16),
            Optional.of(Items.END_CRYSTAL)
        );
        CombatState state = state(inventory);
        StrategicPreparationPlanner planner = new StrategicPreparationPlanner(
            new CandidateGenerator(CandidateFeatureEstimator.conservative())
        );

        List<dev.adrien.crystaloptimizer.action.CombatAction> actions = planner
            .plan(state, config())
            .orElseThrow();

        assertEquals(2, actions.size());
        PlaceObsidian obsidian = assertInstanceOf(PlaceObsidian.class, actions.get(0));
        PlaceCrystal crystal = assertInstanceOf(PlaceCrystal.class, actions.get(1));
        assertEquals(obsidian.pos(), crystal.basePos());
    }

    private static CombatState state(InventoryState inventory) {
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
            CombatRegion.of(Map.of(SUPPORT, Blocks.STONE.defaultBlockState()), Map.of()),
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
