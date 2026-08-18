package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.damage.ExplosionKind;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.timing.PacketDependency;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActionLegalityTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final BlockPos BASE = new BlockPos(0, 64, 0);

    @Test
    void plannedCrystalCannotBeAttackedWithoutServerEntityId() {
        var action = new AttackKnownCrystal(999);
        assertFalse(action.check(state(CombatRegion.empty(), List.of(), Map.of(), inventory(Map.of()), legality(false, List.of()))).legal());
    }

    @Test
    void knownServerCrystalCanBeAttackedWhenInEntityReach() {
        var action = new AttackKnownCrystal(73);
        var crystal = new KnownCrystal(73, new Vec3(0.5, 65.0, 1.5));
        assertTrue(action.check(state(CombatRegion.empty(), List.of(crystal), Map.of(), inventory(Map.of()), legality(false, List.of()))).legal());
    }

    @Test
    void crystalPlacementUsesOneEmptyBlockPlusTwoBlockEntityAabb() {
        var action = new PlaceCrystal(BASE);
        var inventory = inventory(Map.of(Items.END_CRYSTAL, 4));
        var region = CombatRegion.singleBlock(BASE, Blocks.OBSIDIAN.defaultBlockState());
        var clear = state(region, List.of(), Map.of(), inventory, legality(false, List.of()));
        var occupied = state(
            region,
            List.of(),
            Map.of(),
            inventory,
            legality(false, List.of(new AABB(0.0, 65.0, 0.0, 1.0, 67.0, 1.0)))
        );

        assertTrue(action.check(clear).legal());
        assertFalse(action.check(occupied).legal());
    }

    @Test
    void solidBlockTwoAboveDoesNotInvalidate26_1_2CrystalPlacement() {
        var region = CombatRegion.of(
            Map.of(
                BASE, Blocks.OBSIDIAN.defaultBlockState(),
                BASE.above(2), Blocks.OBSIDIAN.defaultBlockState()
            ),
            Map.of()
        );
        var action = new PlaceCrystal(BASE);

        assertTrue(action.check(state(
            region,
            List.of(),
            Map.of(),
            inventory(Map.of(Items.END_CRYSTAL, 1)),
            legality(false, List.of())
        )).legal());
    }

    @Test
    void chargedAnchorOnlyDetonatesWhereRespawnAnchorsDoNotWork() {
        var anchorPos = BASE;
        var region = CombatRegion.singleBlock(anchorPos, Blocks.RESPAWN_ANCHOR.defaultBlockState());
        var anchors = Map.of(anchorPos, new AnchorState(1));
        var action = new DetonateAnchor(anchorPos);
        var inv = inventory(Map.of());

        assertTrue(action.check(state(region, List.of(), anchors, inv, legality(false, List.of()))).legal());
        assertFalse(action.check(state(region, List.of(), anchors, inv, legality(true, List.of()))).legal());
    }

    @Test
    void glowstoneInEitherHandWouldChargeNonFullAnchorInsteadOfDetonating() {
        var anchorPos = BASE;
        var region = CombatRegion.singleBlock(anchorPos, Blocks.RESPAWN_ANCHOR.defaultBlockState());
        var anchors = Map.of(anchorPos, new AnchorState(1));
        var selectedGlowstone = new InventoryState(
            0,
            Map.of(Items.GLOWSTONE, 3),
            Map.of(0, Items.GLOWSTONE),
            Optional.empty()
        );
        var offhandGlowstone = new InventoryState(
            0,
            Map.of(Items.GLOWSTONE, 3),
            Map.of(),
            Optional.of(Items.GLOWSTONE)
        );
        var full = Map.of(anchorPos, new AnchorState(4));
        var action = new DetonateAnchor(anchorPos);
        var context = legality(false, List.of());

        assertFalse(action.check(state(region, List.of(), anchors, selectedGlowstone, context)).legal());
        assertFalse(action.check(state(region, List.of(), anchors, offhandGlowstone, context)).legal());
        assertTrue(action.check(state(region, List.of(), full, selectedGlowstone, context)).legal());
    }

    @Test
    void dependenciesDistinguishClientPredictionFromNewEntityFeedback() {
        assertEquals(PacketDependency.NONE, new AttackKnownCrystal(73).dependency());
        assertEquals(PacketDependency.NONE, new DetonateAnchor(BASE).dependency());
        assertEquals(PacketDependency.CLIENT_PREDICTION, new PlaceCrystal(BASE).dependency());
        assertEquals(PacketDependency.CLIENT_PREDICTION, new PlaceAnchor(BASE).dependency());
        assertEquals(PacketDependency.CLIENT_PREDICTION, new ChargeAnchor(BASE).dependency());
        assertEquals(PacketDependency.CLIENT_PREDICTION, new PlaceObsidian(BASE).dependency());
        assertEquals(PacketDependency.NONE, new SelectHotbarSlot(0).dependency());
        assertEquals(PacketDependency.LOCAL_STATE, new Rotate(0.0f, 0.0f).dependency());
    }

    @Test
    void attackingKnownCrystalRemovesItAndSchedulesExactlyOneCrystalExplosion() {
        var crystal = new KnownCrystal(73, new Vec3(0.5, 65.0, 1.5));
        var initial = state(
            CombatRegion.empty(),
            List.of(crystal),
            Map.of(),
            inventory(Map.of()),
            legality(false, List.of())
        );

        var outcome = new AttackKnownCrystal(73).simulate(initial, SimulationServices.defaults());

        assertTrue(outcome.state().crystals().isEmpty());
        assertEquals(1, outcome.scheduledExplosions().size());
        assertEquals(ExplosionKind.CRYSTAL, outcome.scheduledExplosions().getFirst().kind());
    }

    @Test
    void placeCrystalSimulationNeverFabricatesAClientEntityId() {
        var action = new PlaceCrystal(BASE);
        var initial = state(
            CombatRegion.singleBlock(BASE, Blocks.OBSIDIAN.defaultBlockState()),
            List.of(),
            Map.of(),
            inventory(Map.of(Items.END_CRYSTAL, 2)),
            legality(false, List.of())
        );

        var outcome = action.simulate(initial, SimulationServices.defaults());

        assertTrue(outcome.state().crystals().isEmpty());
        assertTrue(outcome.expectsNewEntityFeedback());
        assertEquals(1, outcome.state().inventory().count(Items.END_CRYSTAL));
    }

    @Test
    void predictedAnchorSetupMutatesOnlyTheBranchAndNeedsNoNewEntityId() {
        var setupInventory = new InventoryState(
            0,
            Map.of(Items.RESPAWN_ANCHOR, 1, Items.GLOWSTONE, 1),
            Map.of(0, Items.RESPAWN_ANCHOR, 1, Items.GLOWSTONE),
            Optional.empty()
        );
        var initial = state(
            CombatRegion.empty(),
            List.of(),
            Map.of(),
            setupInventory,
            legality(false, List.of())
        );

        var placed = new PlaceAnchor(BASE).simulate(initial, SimulationServices.defaults());
        var glowstoneSelected = new SelectHotbarSlot(1).simulate(placed.state(), SimulationServices.defaults());
        var charged = new ChargeAnchor(BASE).simulate(glowstoneSelected.state(), SimulationServices.defaults());
        var emptyHandSelected = new SelectHotbarSlot(2).simulate(charged.state(), SimulationServices.defaults());
        var detonated = new DetonateAnchor(BASE).simulate(emptyHandSelected.state(), SimulationServices.defaults());

        assertFalse(initial.anchors().containsKey(BASE));
        assertTrue(placed.state().geometry().getBlockState(BASE).is(Blocks.RESPAWN_ANCHOR));
        assertEquals(0, placed.state().anchors().get(BASE).charges());
        assertEquals(1, charged.state().anchors().get(BASE).charges());
        assertFalse(detonated.state().anchors().containsKey(BASE));
        assertTrue(detonated.state().geometry().getBlockState(BASE).isAir());
        assertFalse(detonated.expectsNewEntityFeedback());
        assertEquals(1, detonated.scheduledExplosions().size());
        assertEquals(ExplosionKind.ANCHOR, detonated.scheduledExplosions().getFirst().kind());
    }

    @Test
    void crystalRemovedByAnotherExplosionDoesNotScheduleAnotherCrystalExplosion() {
        var first = new KnownCrystal(73, new Vec3(0.5, 65.0, 0.5));
        var second = new KnownCrystal(74, new Vec3(1.5, 65.0, 0.5));
        var initial = state(
            CombatRegion.empty(),
            List.of(first, second),
            Map.of(),
            inventory(Map.of()),
            legality(false, List.of())
        );

        var outcome = SimulationServices.defaults().removeCrystalsHitByExplosion(initial, List.of(74));

        assertTrue(outcome.state().crystals().stream().anyMatch(c -> c.entityId() == 73));
        assertFalse(outcome.state().crystals().stream().anyMatch(c -> c.entityId() == 74));
        assertTrue(outcome.scheduledExplosions().isEmpty());
    }

    private static CombatState state(
        CombatRegion region,
        List<KnownCrystal> crystals,
        Map<BlockPos, AnchorState> anchors,
        InventoryState inventory,
        LegalitySnapshot legality
    ) {
        var snapshot = new CombatSnapshot(
            1L,
            SELF,
            region,
            Map.of(
                SELF, SimCombatant.testPlayer(20.0f),
                TARGET, SimCombatant.testPlayer(20.0f)
            ),
            crystals,
            anchors,
            inventory,
            TimingState.unknown(),
            legality
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }

    private static InventoryState inventory(Map<Item, Integer> counts) {
        if (counts.size() == 1) {
            Item selected = counts.keySet().iterator().next();
            return new InventoryState(0, counts, Map.of(0, selected), Optional.empty());
        }
        return new InventoryState(0, counts, Map.of(), Optional.empty());
    }

    private static LegalitySnapshot legality(boolean respawnAnchorWorks, List<AABB> entities) {
        return new LegalitySnapshot(
            new Vec3(0.5, 65.5, -2.0),
            5.0,
            5.0,
            entities,
            respawnAnchorWorks
        );
    }
}
