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
        assertEquals(-1, HotbarSlotPicker.findMaceSlot(new boolean[8], 4));
    }

    @Test void bufferPrefersEmptySlotButSkipsSelectedAndMaceSlots() {
        boolean[] empty = new boolean[9];
        empty[1] = true;
        empty[2] = true;
        assertEquals(1, HotbarSlotPicker.findBufferSlot(empty, 0, 2));
    }

    @Test void bufferFallsBackToOccupiedNonProtectedSlot() {
        boolean[] empty = new boolean[9];
        int slot = HotbarSlotPicker.findBufferSlot(empty, 0, 2);
        assertEquals(1, slot);
        assertNotEquals(0, slot);
        assertNotEquals(2, slot);
    }
}
