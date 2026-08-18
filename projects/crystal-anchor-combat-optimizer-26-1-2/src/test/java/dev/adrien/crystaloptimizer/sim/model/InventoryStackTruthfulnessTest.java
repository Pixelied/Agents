package dev.adrien.crystaloptimizer.sim.model;

import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryStackTruthfulnessTest {
    @Test
    void exhaustingSelectedStackClearsOnlyThatSlotWhileReserveCopiesRemain() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.END_CRYSTAL, 65),
            Map.of(0, Items.END_CRYSTAL, 1, Items.END_CRYSTAL),
            Map.of(0, 1, 1, 64),
            Optional.empty()
        );

        InventoryState next = inventory.consume(Items.END_CRYSTAL, 1);

        assertEquals(64, next.count(Items.END_CRYSTAL));
        assertTrue(next.selectedItem().isEmpty(),
            "aggregate reserve crystals must not keep an exhausted selected stack magically usable");
        assertEquals(0, next.hotbarCount(0));
        assertEquals(64, next.hotbarCount(1));
        assertEquals(Items.END_CRYSTAL, next.hotbarItems().get(1));
    }

    @Test
    void consumingFromSelectedStackDecrementsItsExactQuantity() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.GLOWSTONE, 10),
            Map.of(0, Items.GLOWSTONE),
            Map.of(0, 3),
            Optional.empty()
        );

        InventoryState next = inventory.consume(Items.GLOWSTONE, 1);

        assertEquals(9, next.count(Items.GLOWSTONE));
        assertEquals(2, next.hotbarCount(0));
        assertEquals(Items.GLOWSTONE, next.selectedItem().orElseThrow());
    }

    @Test
    void selectingAnotherRealStackAfterExhaustionRestoresUsableHandState() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.RESPAWN_ANCHOR, 5),
            Map.of(0, Items.RESPAWN_ANCHOR, 4, Items.RESPAWN_ANCHOR),
            Map.of(0, 1, 4, 4),
            Optional.empty()
        );

        InventoryState exhausted = inventory.consume(Items.RESPAWN_ANCHOR, 1);
        InventoryState switched = exhausted.withSelectedHotbarSlot(4);

        assertTrue(exhausted.selectedItem().isEmpty());
        assertEquals(Items.RESPAWN_ANCHOR, switched.selectedItem().orElseThrow());
        assertEquals(4, switched.hotbarCount(4));
    }
}
