package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.AnchorState;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

final class OffhandInteractionLegalityTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000b01");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000b02");
    private static final BlockPos BASE = new BlockPos(0, 64, 0);

    @Test
    void crystalPlacementIsLegalWhenCrystalIsOnlyInOffhand() {
        InventoryState inventory = offhand(Items.END_CRYSTAL, 4);
        CombatState state = state(
            CombatRegion.singleBlock(BASE, Blocks.OBSIDIAN.defaultBlockState()),
            Map.of(),
            inventory
        );

        assertTrue(new PlaceCrystal(BASE).check(state).legal());
    }

    @Test
    void anchorChargeIsLegalWhenGlowstoneIsOnlyInOffhand() {
        InventoryState inventory = offhand(Items.GLOWSTONE, 4);
        CombatState state = state(
            CombatRegion.singleBlock(BASE, Blocks.RESPAWN_ANCHOR.defaultBlockState()),
            Map.of(BASE, new AnchorState(1)),
            inventory
        );

        assertTrue(new ChargeAnchor(BASE).check(state).legal());
    }

    private static InventoryState offhand(Item item, int count) {
        return new InventoryState(
            0,
            Map.of(item, count),
            Map.of(),
            Map.of(),
            Optional.of(item)
        );
    }

    private static CombatState state(
        CombatRegion region,
        Map<BlockPos, AnchorState> anchors,
        InventoryState inventory
    ) {
        CombatSnapshot snapshot = new CombatSnapshot(
            1L,
            SELF,
            region,
            Map.of(
                SELF, SimCombatant.testPlayer(20.0f),
                TARGET, SimCombatant.testPlayer(20.0f)
            ),
            List.of(),
            anchors,
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
