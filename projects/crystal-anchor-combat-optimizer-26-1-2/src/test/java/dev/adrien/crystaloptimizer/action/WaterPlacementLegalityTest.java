package dev.adrien.crystaloptimizer.action;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

final class WaterPlacementLegalityTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @Test
    void obsidianCanReplaceSupportedWaterLikeVanillaBlockPlacement() {
        CombatRegion region = CombatRegion.of(
            Map.of(
                POS, Blocks.WATER.defaultBlockState(),
                POS.below(), Blocks.STONE.defaultBlockState()
            ),
            Map.of()
        );

        assertTrue(new PlaceObsidian(POS).check(state(region, Items.OBSIDIAN)).legal());
    }

    @Test
    void anchorCanReplaceSupportedWaterLikeVanillaBlockPlacement() {
        CombatRegion region = CombatRegion.of(
            Map.of(
                POS, Blocks.WATER.defaultBlockState(),
                POS.below(), Blocks.STONE.defaultBlockState()
            ),
            Map.of()
        );

        assertTrue(new PlaceAnchor(POS).check(state(region, Items.RESPAWN_ANCHOR)).legal());
    }

    @Test
    void crystalStillCannotBePlacedWhenWaterOccupiesDirectAboveBlock() {
        CombatRegion region = CombatRegion.of(
            Map.of(
                POS, Blocks.OBSIDIAN.defaultBlockState(),
                POS.above(), Blocks.WATER.defaultBlockState()
            ),
            Map.of()
        );

        assertFalse(new PlaceCrystal(POS).check(state(region, Items.END_CRYSTAL)).legal());
    }

    @Test
    void crystalBecomesLegalWhenDirectAboveBlockIsActuallyEmpty() {
        CombatRegion region = CombatRegion.singleBlock(POS, Blocks.OBSIDIAN.defaultBlockState());

        assertTrue(new PlaceCrystal(POS).check(state(region, Items.END_CRYSTAL)).legal());
    }

    private static CombatState state(CombatRegion region, Item selectedItem) {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(selectedItem, 4),
            Map.of(0, selectedItem),
            Optional.empty()
        );
        CombatSnapshot snapshot = new CombatSnapshot(
            1L,
            SELF,
            region,
            Map.of(
                SELF, SimCombatant.testPlayer(20.0f),
                TARGET, SimCombatant.testPlayer(20.0f)
            ),
            List.of(),
            Map.of(),
            inventory,
            TimingState.unknown(),
            new LegalitySnapshot(
                new Vec3(0.5, 65.5, -2.0),
                5.0,
                5.0,
                List.of(),
                false
            )
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }
}
