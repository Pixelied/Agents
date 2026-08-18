package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BattlefieldSetupCandidateTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000911");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000912");
    private static final BlockPos LOCAL = new BlockPos(1, 64, 2);
    private static final BlockPos LOCAL_SUPPORT = LOCAL.below();
    private static final BlockPos FAR = new BlockPos(20, 64, 20);
    private static final BlockPos FAR_SUPPORT = FAR.below();

    @Test
    void placeAnchorAndObsidianRejectFloatingAirWithNoAdjacentSupportFace() {
        CombatState anchorState = state(Items.RESPAWN_ANCHOR, CombatRegion.empty());
        CombatState obsidianState = state(Items.OBSIDIAN, CombatRegion.empty());

        assertFalse(new PlaceAnchor(LOCAL).check(anchorState).legal());
        assertFalse(new PlaceObsidian(LOCAL).check(obsidianState).legal());
    }

    @Test
    void generatorFindsNearbySupportedAnchorPositionButIgnoresDistantOne() {
        CandidateGenerator generator = new CandidateGenerator(CandidateFeatureEstimator.conservative());
        CombatState state = state(Items.RESPAWN_ANCHOR, supportedRegion());

        List<Candidate> candidates = generator.generate(state);

        assertTrue(candidates.stream().anyMatch(candidate ->
            candidate.action() instanceof PlaceAnchor place && place.pos().equals(LOCAL)));
        assertFalse(candidates.stream().anyMatch(candidate ->
            candidate.action() instanceof PlaceAnchor place && place.pos().equals(FAR)));
    }

    @Test
    void generatorFindsNearbySupportedObsidianPositionButIgnoresDistantOne() {
        CandidateGenerator generator = new CandidateGenerator(CandidateFeatureEstimator.conservative());
        CombatState state = state(Items.OBSIDIAN, supportedRegion());

        List<Candidate> candidates = generator.generate(state);

        assertTrue(candidates.stream().anyMatch(candidate ->
            candidate.action() instanceof PlaceObsidian place && place.pos().equals(LOCAL)));
        assertFalse(candidates.stream().anyMatch(candidate ->
            candidate.action() instanceof PlaceObsidian place && place.pos().equals(FAR)));
    }

    private static CombatRegion supportedRegion() {
        return CombatRegion.of(
            Map.of(
                LOCAL_SUPPORT, Blocks.OBSIDIAN.defaultBlockState(),
                FAR_SUPPORT, Blocks.OBSIDIAN.defaultBlockState()
            ),
            Map.of()
        );
    }

    private static CombatState state(Item selected, CombatRegion region) {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(selected, 8),
            Map.of(0, selected),
            Optional.empty()
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
                List.of(
                    spatial.get(SELF).boundingBox(),
                    spatial.get(TARGET).boundingBox()
                ),
                false
            ),
            spatial,
            Difficulty.NORMAL
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }
}
