package dev.pixelied.maceswap;

/** Pure hotbar slot selection logic, kept separate so the safety rules are unit-testable. */
public final class HotbarSlotPicker {
    public static final int HOTBAR_SIZE = 9;

    private HotbarSlotPicker() {}

    /** Prefer an already-selected mace, otherwise choose the first mace in hotbar order. */
    public static int findMaceSlot(boolean[] maceSlots, int selectedSlot) {
        if (maceSlots == null || maceSlots.length != HOTBAR_SIZE) return -1;
        if (isValidSlot(selectedSlot) && maceSlots[selectedSlot]) return selectedSlot;
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (maceSlots[slot]) return slot;
        }
        return -1;
    }

    /** Prefer an empty buffer; never overwrite the selected slot or the mace slot. */
    public static int findBufferSlot(boolean[] emptySlots, int selectedSlot, int maceSlot) {
        if (emptySlots == null || emptySlots.length != HOTBAR_SIZE) return -1;
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (slot != selectedSlot && slot != maceSlot && emptySlots[slot]) return slot;
        }
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (slot != selectedSlot && slot != maceSlot) return slot;
        }
        return -1;
    }

    private static boolean isValidSlot(int slot) {
        return slot >= 0 && slot < HOTBAR_SIZE;
    }
}
