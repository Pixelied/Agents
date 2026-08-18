package dev.adrien.crystaloptimizer.planner;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.SelectHotbarSlot;
import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.candidate.CandidateFeatureEstimator;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.candidate.CandidatePruner;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PrepareModePlannerTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000921");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000922");
    private static final BlockPos LOCAL = new BlockPos(1, 64, 2);
    private static final BlockPos SUPPORT = LOCAL.below();

    @Test
    void noPressureLineChoosesConcreteAnchorPreparationInsteadOfDoingNothing() {
        BeamPlanner planner = planner();
        CombatPlan plan = planner.plan(
            state(selectedAnchorInventory(), List.of()),
            new PlannerBudget(16, 2, 50_000_000)
        );

        assertFalse(plan.actions().isEmpty(), "prepare mode must beat an idle root when legal setup exists");
        assertInstanceOf(PlaceAnchor.class, plan.actions().getFirst());
        assertFalse(plan.lethal());
    }

    @Test
    void wrongHandPreparationStartsWithRealHotbarSelection() {
        BeamPlanner planner = planner();
        CombatPlan plan = planner.plan(
            state(anchorInSecondSlotInventory(), List.of()),
            new PlannerBudget(16, 3, 50_000_000)
        );

        assertFalse(plan.actions().isEmpty());
        SelectHotbarSlot select = assertInstanceOf(SelectHotbarSlot.class, plan.actions().getFirst());
        assertEquals(1, select.slot());
    }

    @Test
    void immediateCrystalPressureStillBeatsPreparation() {
        BeamPlanner planner = planner();
        KnownCrystal crystal = new KnownCrystal(701, new Vec3(0.5, 65.0, 2.5));
        CombatPlan plan = planner.plan(
            state(selectedAnchorInventory(), List.of(crystal)),
            new PlannerBudget(16, 2, 50_000_000)
        );

        AttackKnownCrystal attack = assertInstanceOf(AttackKnownCrystal.class, plan.actions().getFirst());
        assertEquals(701, attack.entityId());
        assertTrue(plan.score().threatNeutralization() > 0.0);
    }

    private static BeamPlanner planner() {
        return new BeamPlanner(
            new CandidateGenerator(CandidateFeatureEstimator.conservative()),
            new CandidatePruner(),
            SimulationServices.defaults(),
            RiskBudget.adaptive()
        );
    }

    private static CombatState state(InventoryState inventory, List<KnownCrystal> crystals) {
        CombatRegion region = CombatRegion.singleBlock(SUPPORT, Blocks.OBSIDIAN.defaultBlockState());
        SimCombatant self = SimCombatant.testPlayer(20.0f);
        SimCombatant target = SimCombatant.testPlayer(20.0f);
        Map<UUID, CombatantSpatialState> spatial = Map.of(
            SELF, new CombatantSpatialState(
                new Vec3(0.5, 64.0, -7.0),
                new AABB(0.2, 64.0, -7.3, 0.8, 65.8, -6.7),
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
            crystals,
            Map.of(),
            inventory,
            TimingState.unknown(),
            new LegalitySnapshot(
                new Vec3(0.5, 65.5, -7.0),
                10.0,
                10.0,
                List.of(spatial.get(SELF).boundingBox(), spatial.get(TARGET).boundingBox()),
                false
            ),
            spatial,
            Difficulty.NORMAL
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }

    private static InventoryState selectedAnchorInventory() {
        return new InventoryState(
            0,
            Map.of(Items.RESPAWN_ANCHOR, 4),
            Map.of(0, Items.RESPAWN_ANCHOR),
            Optional.empty()
        );
    }

    private static InventoryState anchorInSecondSlotInventory() {
        return new InventoryState(
            0,
            Map.of(Items.DIAMOND_SWORD, 1, Items.RESPAWN_ANCHOR, 4),
            Map.of(0, Items.DIAMOND_SWORD, 1, Items.RESPAWN_ANCHOR),
            Optional.empty()
        );
    }
}
