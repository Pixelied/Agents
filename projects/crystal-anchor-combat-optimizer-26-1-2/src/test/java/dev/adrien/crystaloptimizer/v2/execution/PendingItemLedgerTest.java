package dev.adrien.crystaloptimizer.v2.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class PendingItemLedgerTest {
    @Test
    void predictedPlacementCannotSpendOneVisibleCrystalTwice() {
        PendingItemLedger ledger = new PendingItemLedger();
        ledger.reserve(100L, Items.END_CRYSTAL, 1, 1);
        assertEquals(0, ledger.available(Items.END_CRYSTAL, 1));
        assertThrows(
            IllegalStateException.class,
            () -> ledger.reserve(101L, Items.END_CRYSTAL, 1, 1)
        );

        ledger.release(100L);
        assertEquals(1, ledger.available(Items.END_CRYSTAL, 1));
    }

    @Test
    void duplicateActionIdCannotReplaceAnExistingReservation() {
        PendingItemLedger ledger = new PendingItemLedger();
        ledger.reserve(42L, Items.END_CRYSTAL, 1, 2);

        assertThrows(
            IllegalStateException.class,
            () -> ledger.reserve(42L, Items.END_CRYSTAL, 1, 2)
        );
        assertEquals(1, ledger.reserved(Items.END_CRYSTAL));
    }
}
