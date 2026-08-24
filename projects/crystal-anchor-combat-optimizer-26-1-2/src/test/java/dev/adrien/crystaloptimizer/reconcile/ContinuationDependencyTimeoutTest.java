package dev.adrien.crystaloptimizer.reconcile;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContinuationDependencyTimeoutTest {
    @Test
    void unconfirmedDependencyExpiresWithoutPretendingItWasSatisfied() {
        ContinuationDependency.CrystalGone dependency = new ContinuationDependency.CrystalGone(
            42,
            new BlockPos(4, 64, 7),
            2_000L
        );

        assertFalse(dependency.expired(2_000L));
        assertTrue(dependency.expired(2_001L));
    }
}
