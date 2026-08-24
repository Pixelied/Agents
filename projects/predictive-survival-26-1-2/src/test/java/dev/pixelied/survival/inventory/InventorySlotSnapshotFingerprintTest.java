package dev.pixelied.survival.inventory;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventorySlotSnapshotFingerprintTest {
    @Test
    void sameContentsRequiresSameItemComponents() {
        InventorySlotSnapshot original = new InventorySlotSnapshot(
            0, "minecraft:totem_of_undying", 111, 1, true,
            Optional.empty(), Optional.empty(), Optional.empty()
        );
        InventorySlotSnapshot same = new InventorySlotSnapshot(
            0, "minecraft:totem_of_undying", 111, 1, true,
            Optional.empty(), Optional.empty(), Optional.empty()
        );
        InventorySlotSnapshot changedComponents = new InventorySlotSnapshot(
            0, "minecraft:totem_of_undying", 222, 1, true,
            Optional.empty(), Optional.empty(), Optional.empty()
        );

        assertTrue(original.sameContents(same));
        assertFalse(original.sameContents(changedComponents));
    }
}
