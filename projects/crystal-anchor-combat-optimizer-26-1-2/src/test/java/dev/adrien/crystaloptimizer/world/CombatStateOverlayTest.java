package dev.adrien.crystaloptimizer.world;

import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CombatStateOverlayTest {
    @Test
    void branchOverlayDoesNotMutateBaseOrSibling() {
        var stone = new BlockPos(0, 64, 0);
        var obsidian = new BlockPos(1, 64, 0);
        var base = CombatRegion.singleBlock(stone, Blocks.STONE.defaultBlockState());

        var removedBranch = new BlockDeltaOverlay(base).withRemoved(stone);
        var placedBranch = new BlockDeltaOverlay(base).withPlaced(obsidian, Blocks.OBSIDIAN.defaultBlockState());

        assertTrue(removedBranch.getBlockState(stone).isAir());
        assertTrue(placedBranch.getBlockState(stone).is(Blocks.STONE));
        assertTrue(base.getBlockState(stone).is(Blocks.STONE));
        assertTrue(base.getBlockState(obsidian).isAir());
    }

    @Test
    void snapshotCopiesMutableInputsAndCombatStateKeepsTheWorldRevision() {
        UUID selfId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        var crystal = new KnownCrystal(73, new Vec3(2.5, 65.0, 0.5));
        var anchorPos = new BlockPos(1, 64, 1);
        var crystals = new ArrayList<>(List.of(crystal));
        var anchors = new HashMap<>(Map.of(anchorPos, new AnchorState(2)));
        var combatants = new HashMap<>(Map.of(
            selfId, SimCombatant.testPlayer(20.0f),
            targetId, SimCombatant.testPlayer(18.0f)
        ));

        var snapshot = new CombatSnapshot(
            42L,
            selfId,
            CombatRegion.empty(),
            combatants,
            crystals,
            anchors,
            InventoryState.empty(),
            TimingState.unknown()
        );
        crystals.clear();
        anchors.clear();
        combatants.clear();

        var state = CombatState.fromSnapshot(snapshot, targetId);

        assertEquals(42L, state.base().worldRevision());
        assertEquals(1, state.crystals().size());
        assertEquals(2, state.anchors().get(anchorPos).charges());
        assertEquals(20.0f, state.self().health(), 0.0001f);
        assertEquals(18.0f, state.target().health(), 0.0001f);
        assertThrows(UnsupportedOperationException.class, () -> state.crystals().add(crystal));
        assertThrows(UnsupportedOperationException.class, () -> state.anchors().clear());
    }

    @Test
    void knownCrystalRequiresARealPositiveServerEntityId() {
        assertThrows(IllegalArgumentException.class, () -> new KnownCrystal(0, Vec3.ZERO));
    }

    @Test
    void unobservedExplosionTerrainNeverPretendsServerRngIsExact() {
        var base = new BlockDeltaOverlay(CombatRegion.singleBlock(
            new BlockPos(1, 64, 0), Blocks.STONE.defaultBlockState()
        ));

        var outcomes = ExplosionTerrainPredictor.unobserved(
            ExplosionContext.crystal(new Vec3(0.5, 64.5, 0.5)),
            base
        );

        assertFalse(outcomes.isEmpty());
        assertTrue(outcomes.stream().noneMatch(ExplosionTerrainOutcome::exact));
        assertTrue(outcomes.stream().allMatch(outcome -> outcome.overlay() != null));
    }
}
