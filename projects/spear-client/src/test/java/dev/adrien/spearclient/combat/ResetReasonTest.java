package dev.adrien.spearclient.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResetReasonTest {
    @Test
    void localOwnershipFailuresReleaseOwnedItemUse() {
        assertTrue(ResetReason.CONFIG_DISABLED.shouldReleaseOwnedUse());
        assertTrue(ResetReason.TARGET_LOST.shouldReleaseOwnedUse());
        assertTrue(ResetReason.LOST_SPEAR.shouldReleaseOwnedUse());
    }

    @Test
    void transportAndPlayerReplacementResetsStayPacketSilent() {
        assertFalse(ResetReason.CORRECTION.shouldReleaseOwnedUse());
        assertFalse(ResetReason.DISCONNECT.shouldReleaseOwnedUse());
        assertFalse(ResetReason.LEVEL_CHANGE.shouldReleaseOwnedUse());
        assertFalse(ResetReason.RESPAWN.shouldReleaseOwnedUse());
        assertFalse(ResetReason.DEATH.shouldReleaseOwnedUse());
    }
}
