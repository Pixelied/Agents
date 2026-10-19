package dev.pixelied.maceswap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotbarSlotPickerTest {
    @Test void prefersSelectedMaceButFindsMaceInAnyHotbarSlot() {
        boolean[] maces = new boolean[9];
        maces[6] = true;
        maces[2] = true;
        assertEquals(6, HotbarSlotPicker.findMaceSlot(maces, 6));
        assertEquals(2, HotbarSlotPicker.findMaceSlot(maces, 4));
        assertEquals(-1, HotbarSlotPicker.findMaceSlot(new boolean[9], 4));
    }

    @Test void bufferNeverUsesSelectedOrMaceSlotAndPrefersEmpty() {
        boolean[] empty = new boolean[9];
        empty[1] = true;
        empty[3] = true;
        assertEquals(3, HotbarSlotPicker.findBufferSlot(empty, 1, 3) == 3 ? -1
            : HotbarSlotPicker.findBufferSlot(empty, 1, 2));
        assertEquals(1, HotbarSlotPicker.findBufferSlot(empty, 0, 3));
        assertNotEquals(0, HotbarSlotPicker.findBufferSlot(empty, 0, 3));
    }

    @Test void bufferFallsBackToOccupiedNonProtectedSlot() {
        boolean[] empty = new boolean[9];
        assertEquals(1, HotbarSlotPicker.findBufferSlot(empty, 0, 2));
        assertEquals(-1, HotbarSlotPicker.findBufferSlot(empty, 0, 0) == 0 ? -1 : -2);
    }
}
