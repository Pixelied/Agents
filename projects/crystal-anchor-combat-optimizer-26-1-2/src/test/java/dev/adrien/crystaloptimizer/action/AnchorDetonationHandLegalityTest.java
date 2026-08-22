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
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AnchorDetonationHandLegalityTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000d01");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000d02");
    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @Test
    void offhandGlowstoneDoesNotBlockMainhandDetonation() {
        CombatState state = state(inventory(Items.DIAMOND_SWORD, Optional.of(Items.GLOWSTONE)));

        assertTrue(new DetonateAnchor(POS).check(state).legal());
    }

    @Test
    void mainhandGlowstoneCanDetonateThroughNonGlowstoneOffhand() {
        CombatState state = state(inventory(Items.GLOWSTONE, Optional.of(Items.TOTEM_OF_UNDYING)));

        assertTrue(new DetonateAnchor(POS).check(state).legal());
    }

    @Test
    void partialAnchorRejectsWhenBothInteractionHandsWouldCharge() {
        CombatState state = state(inventory(Items.GLOWSTONE, Optional.of(Items.GLOWSTONE)));

        assertFalse(new DetonateAnchor(POS).check(state).legal());
    }

    private static InventoryState inventory(Item selected, Optional<Item> offhand) {
        Map<Item, Integer> counts = offhand
            .filter(selected::equals)
            .map(ignored -> Map.of(selected, 2))
            .orElseGet(() -> offhand
                .map(item -> Map.of(selected, 1, item, 1))
                .orElseGet(() -> Map.of(selected, 1)));
        return new InventoryState(
            0,
            counts,
            Map.of(0, selected),
            Map.of(0, 1),
            offhand
        );
    }

    private static CombatState state(InventoryState inventory) {
        var blockState = Blocks.RESPAWN_ANCHOR.defaultBlockState()
            .setValue(RespawnAnchorBlock.CHARGE, 1);
        CombatSnapshot snapshot = new CombatSnapshot(
            1L,
            SELF,
            CombatRegion.singleBlock(POS, blockState),
            Map.of(
                SELF, SimCombatant.testPlayer(20.0f),
                TARGET, SimCombatant.testPlayer(20.0f)
            ),
            List.of(),
            Map.of(POS, new AnchorState(1)),
            inventory,
            TimingState.unknown(),
            new LegalitySnapshot(new Vec3(0.5, 65.5, -2.0), 5.0, 5.0, List.of(), false)
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }
}
