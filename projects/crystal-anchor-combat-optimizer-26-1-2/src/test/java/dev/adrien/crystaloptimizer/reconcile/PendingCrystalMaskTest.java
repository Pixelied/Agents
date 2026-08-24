package dev.adrien.crystaloptimizer.reconcile;

import java.util.Set;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PendingCrystalMaskTest {
    @Test
    void pendingRemovalIsReversibleAndExpiresWithoutMutatingWorldState() {
        PendingCrystalMask mask = new PendingCrystalMask();
        BlockPos base = new BlockPos(4, 64, 7);
        mask.markAttacked(42, base, 2_000L);

        assertTrue(mask.isPendingRemoval(42, base, 1_000L));
        assertFalse(mask.isPendingRemoval(43, base, 1_000L));
        assertFalse(mask.isPendingRemoval(42, base, 2_001L));
    }

    @Test
    void confirmedRemovalConsumesPendingEntryAndReturnsObservableFact() {
        PendingCrystalMask mask = new PendingCrystalMask();
        BlockPos base = new BlockPos(2, 70, 3);
        mask.markAttacked(91, base, 5_000L);

        PendingCrystalMask.Removal removed = mask.confirmRemoved(91).orElseThrow();

        assertEquals(91, removed.entityId());
        assertEquals(base, removed.basePos());
        assertFalse(mask.isPendingRemoval(91, base, 1_500L));
    }

    @Test
    void reconcileConfirmsOnlyPendingCrystalsThatAreActuallyAbsent() {
        PendingCrystalMask mask = new PendingCrystalMask();
        BlockPos first = new BlockPos(1, 64, 1);
        BlockPos second = new BlockPos(2, 64, 2);
        mask.markAttacked(10, first, 4_000L);
        mask.markAttacked(11, second, 4_000L);

        Set<PendingCrystalMask.Removal> removed = mask.reconcile(Set.of(11), 2_000L);

        assertEquals(Set.of(new PendingCrystalMask.Removal(10, first)), removed);
        assertFalse(mask.isPendingRemoval(10, first, 2_000L));
        assertTrue(mask.isPendingRemoval(11, second, 2_000L));
    }
}
