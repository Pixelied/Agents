package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.action.SelectHotbarSlot;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StrategicPreparationPlannerTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000971");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000972");
    private static final BlockPos SETUP = new BlockPos(1, 64, 2);
    private static final BlockPos SUPPORT = SETUP.below();
    private static final BlockPos CRYSTAL_BASE = new BlockPos(2, 64, 2);

    private final StrategicPreparationPlanner planner = new StrategicPreparationPlanner(
        new CandidateGenerator(CandidateFeatureEstimator.conservative())
    );

    @Test
    void coldCrystalStartBuildsCompleteObsidianToCrystalChain() {
        CombatState state = state(
            inventory(
                0,
                Map.of(0, Items.DIAMOND_SWORD, 1, Items.OBSIDIAN, 2, Items.END_CRYSTAL),
                Map.of(Items.DIAMOND_SWORD, 1, Items.OBSIDIAN, 16, Items.END_CRYSTAL, 16)
            ),
            CombatRegion.of(Map.of(SUPPORT, Blocks.STONE.defaultBlockState()), Map.of())
        );

        List<CombatAction> actions = planner.plan(state, config(true, false)).orElseThrow();

        assertEquals(4, actions.size());
        assertEquals(1, assertInstanceOf(SelectHotbarSlot.class, actions.get(0)).slot());
        PlaceObsidian support = assertInstanceOf(PlaceObsidian.class, actions.get(1));
        assertEquals(2, assertInstanceOf(SelectHotbarSlot.class, actions.get(2)).slot());
        PlaceCrystal crystal = assertInstanceOf(PlaceCrystal.class, actions.get(3));
        assertEquals(support.pos(), crystal.basePos());
    }

    @Test
    void existingCrystalBaseSelectsCrystalThenPlacesItWithoutNewSupport() {
        CombatState state = state(
            inventory(
                0,
                Map.of(0, Items.DIAMOND_SWORD, 2, Items.END_CRYSTAL),
                Map.of(Items.DIAMOND_SWORD, 1, Items.END_CRYSTAL, 16)
            ),
            CombatRegion.of(Map.of(CRYSTAL_BASE, Blocks.OBSIDIAN.defaultBlockState()), Map.of())
        );

        List<CombatAction> actions = planner.plan(state, config(true, false)).orElseThrow();

        assertEquals(2, actions.size());
        assertEquals(2, assertInstanceOf(SelectHotbarSlot.class, actions.get(0)).slot());
        PlaceCrystal place = assertInstanceOf(PlaceCrystal.class, actions.get(1));
        assertEquals(CRYSTAL_BASE, place.basePos());
    }

    @Test
    void existingBaseWhileObsidianSelectedSwitchesToCrystalInsteadOfPlacingMoreSupport() {
        CombatState state = state(
            inventory(
                1,
                Map.of(1, Items.OBSIDIAN, 2, Items.END_CRYSTAL),
                Map.of(Items.OBSIDIAN, 16, Items.END_CRYSTAL, 16)
            ),
            CombatRegion.of(
                Map.of(SUPPORT, Blocks.STONE.defaultBlockState(), CRYSTAL_BASE, Blocks.OBSIDIAN.defaultBlockState()),
                Map.of()
            )
        );

        List<CombatAction> actions = planner.plan(state, config(true, false)).orElseThrow();

        assertEquals(2, actions.size());
        assertEquals(2, assertInstanceOf(SelectHotbarSlot.class, actions.get(0)).slot());
        assertEquals(CRYSTAL_BASE, assertInstanceOf(PlaceCrystal.class, actions.get(1)).basePos());
    }

    @Test
    void coldAnchorStartBuildsCompleteAnchorGlowstoneDetonationChain() {
        CombatState state = state(
            inventory(
                0,
                Map.of(0, Items.DIAMOND_SWORD, 3, Items.RESPAWN_ANCHOR, 4, Items.GLOWSTONE),
                Map.of(Items.DIAMOND_SWORD, 1, Items.RESPAWN_ANCHOR, 8, Items.GLOWSTONE, 16)
            ),
            CombatRegion.of(Map.of(SUPPORT, Blocks.STONE.defaultBlockState()), Map.of())
        );

        List<CombatAction> actions = planner.plan(state, config(false, true)).orElseThrow();

        assertEquals(6, actions.size());
        assertEquals(3, assertInstanceOf(SelectHotbarSlot.class, actions.get(0)).slot());
        PlaceAnchor anchor = assertInstanceOf(PlaceAnchor.class, actions.get(1));
        assertEquals(4, assertInstanceOf(SelectHotbarSlot.class, actions.get(2)).slot());
        assertEquals(anchor.pos(), assertInstanceOf(ChargeAnchor.class, actions.get(3)).pos());
        assertEquals(0, assertInstanceOf(SelectHotbarSlot.class, actions.get(4)).slot());
        assertEquals(anchor.pos(), assertInstanceOf(DetonateAnchor.class, actions.get(5)).pos());
    }

    @Test
    void noGlowstoneMeansNoNewAnchorSpend() {
        CombatState state = state(
            inventory(
                0,
                Map.of(0, Items.DIAMOND_SWORD, 3, Items.RESPAWN_ANCHOR),
                Map.of(Items.DIAMOND_SWORD, 1, Items.RESPAWN_ANCHOR, 8)
            ),
            CombatRegion.of(Map.of(SUPPORT, Blocks.STONE.defaultBlockState()), Map.of())
        );

        assertTrue(planner.plan(state, config(false, true)).isEmpty());
    }

    @Test
    void noCrystalMeansNoObsidianSetupSpend() {
        CombatState state = state(
            inventory(
                0,
                Map.of(0, Items.DIAMOND_SWORD, 1, Items.OBSIDIAN),
                Map.of(Items.DIAMOND_SWORD, 1, Items.OBSIDIAN, 16)
            ),
            CombatRegion.of(Map.of(SUPPORT, Blocks.STONE.defaultBlockState()), Map.of())
        );

        assertTrue(planner.plan(state, config(true, false)).isEmpty());
    }

    private static OptimizerConfig config(boolean crystals, boolean anchors) {
        return new OptimizerConfig(
            true,
            OptimizerStrategy.LETHAL_SPEED,
            12.0,
            4.0f,
            12.0f,
            8.0f,
            crystals,
            anchors,
            false,
            RotationMode.ADAPTIVE,
            true
        );
    }

    private static InventoryState inventory(
        int selectedSlot,
        Map<Integer, Item> hotbar,
        Map<Item, Integer> counts
    ) {
        return new InventoryState(selectedSlot, counts, hotbar, Optional.empty());
    }

    private static CombatState state(InventoryState inventory, CombatRegion region) {
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
}
