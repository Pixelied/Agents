package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.action.SelectHotbarSlot;
import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
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
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HandTruthfulnessTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000901");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000902");
    private static final BlockPos CRYSTAL_BASE = new BlockPos(0, 64, 0);
    private static final BlockPos ANCHOR = new BlockPos(1, 64, 0);
    private static final BlockPos EMPTY = new BlockPos(2, 64, 0);

    @Test
    void placeCrystalRequiresCrystalInSelectedMainHand() {
        CombatState state = state(0, Optional.empty());
        PlaceCrystal action = new PlaceCrystal(CRYSTAL_BASE);

        assertFalse(action.check(state).legal());

        CombatState selected = new SelectHotbarSlot(1).simulate(state, SimulationServices.defaults()).state();
        assertTrue(action.check(selected).legal());
    }

    @Test
    void chargeAnchorRequiresGlowstoneInSelectedMainHand() {
        CombatState state = state(0, Optional.empty());
        ChargeAnchor action = new ChargeAnchor(ANCHOR);

        assertFalse(action.check(state).legal());

        CombatState selected = new SelectHotbarSlot(2).simulate(state, SimulationServices.defaults()).state();
        assertTrue(action.check(selected).legal());
    }

    @Test
    void placeAnchorRequiresAnchorInSelectedMainHand() {
        CombatState state = state(0, Optional.empty());
        PlaceAnchor action = new PlaceAnchor(EMPTY);

        assertFalse(action.check(state).legal());

        CombatState selected = new SelectHotbarSlot(3).simulate(state, SimulationServices.defaults()).state();
        assertTrue(action.check(selected).legal());
    }

    @Test
    void placeObsidianRequiresObsidianInSelectedMainHand() {
        CombatState state = state(1, Optional.empty());
        PlaceObsidian action = new PlaceObsidian(EMPTY);

        assertFalse(action.check(state).legal());

        CombatState selected = new SelectHotbarSlot(0).simulate(state, SimulationServices.defaults()).state();
        assertTrue(action.check(selected).legal());
    }

    @Test
    void anchorDetonationOnlyCaresAboutActualMainHandInteractionItem() {
        CombatState state = state(0, Optional.of(Items.GLOWSTONE));

        assertTrue(new DetonateAnchor(ANCHOR).check(state).legal(),
            "offhand glowstone must not block a dispatcher that explicitly interacts with MAIN_HAND");
    }

    @Test
    void generatorOffersRequiredSlotSwitchThenPlacementBecomesCandidate() {
        CandidateGenerator generator = new CandidateGenerator(CandidateFeatureEstimator.conservative());
        CombatState state = state(0, Optional.empty());

        assertTrue(generator.generate(state).stream().anyMatch(candidate ->
            candidate.action() instanceof SelectHotbarSlot select && select.slot() == 1));
        assertFalse(generator.generate(state).stream().anyMatch(candidate ->
            candidate.action() instanceof PlaceCrystal place && place.basePos().equals(CRYSTAL_BASE)));

        CombatState selected = new SelectHotbarSlot(1).simulate(state, SimulationServices.defaults()).state();
        assertTrue(generator.generate(selected).stream().anyMatch(candidate ->
            candidate.action() instanceof PlaceCrystal place && place.basePos().equals(CRYSTAL_BASE)));
    }

    private static CombatState state(int selectedSlot, Optional<net.minecraft.world.item.Item> offhand) {
        var anchorBlock = Blocks.RESPAWN_ANCHOR.defaultBlockState().setValue(RespawnAnchorBlock.CHARGE, 1);
        CombatRegion region = CombatRegion.of(
            Map.of(CRYSTAL_BASE, Blocks.OBSIDIAN.defaultBlockState(), ANCHOR, anchorBlock),
            Map.of()
        );
        InventoryState inventory = new InventoryState(
            selectedSlot,
            Map.of(
                Items.OBSIDIAN, 2,
                Items.END_CRYSTAL, 2,
                Items.GLOWSTONE, 2,
                Items.RESPAWN_ANCHOR, 2
            ),
            Map.of(
                0, Items.OBSIDIAN,
                1, Items.END_CRYSTAL,
                2, Items.GLOWSTONE,
                3, Items.RESPAWN_ANCHOR
            ),
            offhand
        );
        SimCombatant self = SimCombatant.testPlayer(20.0f);
        SimCombatant target = SimCombatant.testPlayer(20.0f);
        Map<UUID, CombatantSpatialState> spatial = Map.of(
            SELF, new CombatantSpatialState(
                new Vec3(0.5, 64.0, -2.0),
                new AABB(0.2, 64.0, -2.3, 0.8, 65.8, -1.7),
                Vec3.ZERO
            ),
            TARGET, new CombatantSpatialState(
                new Vec3(0.5, 64.0, 4.0),
                new AABB(0.2, 64.0, 3.7, 0.8, 65.8, 4.3),
                Vec3.ZERO
            )
        );
        CombatSnapshot snapshot = new CombatSnapshot(
            1L,
            SELF,
            region,
            Map.of(SELF, self, TARGET, target),
            List.of(),
            Map.of(ANCHOR, new AnchorState(1)),
            inventory,
            TimingState.unknown(),
            new LegalitySnapshot(new Vec3(0.5, 65.5, -2.0), 6.0, 6.0, List.of(), false),
            spatial,
            Difficulty.NORMAL
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }
}
