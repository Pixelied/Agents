package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionHoldLeaseTest {
    @Test
    void oneSafeFrameCannotReleaseProtectionBeforeObservationAndReturnUncertaintyExpires() {
        ProtectionHoldLease lease = new ProtectionHoldLease();
        lease.require(ProtectionHoldLease.ProtectionRequirement.lethalThreat(), timing(100), 0L);

        lease.observeSafe(ProtectionHoldLease.SafeEvidence.clean(), timing(101), 0L);

        assertEquals(109L, lease.releaseNotBeforeServerTick(),
            "release must include observation age, outbound processing, and correction-return uncertainty");
        assertTrue(lease.blocksRestoration(108L));
        assertFalse(lease.blocksRestoration(109L));
    }

    @Test
    void renewedDangerResetsTheContinuousSafeWindow() {
        ProtectionHoldLease lease = new ProtectionHoldLease();
        lease.require(ProtectionHoldLease.ProtectionRequirement.lethalThreat(), timing(100), 0L);
        lease.observeSafe(ProtectionHoldLease.SafeEvidence.clean(), timing(101), 0L);
        assertEquals(109L, lease.releaseNotBeforeServerTick());

        lease.require(ProtectionHoldLease.ProtectionRequirement.lethalOpportunity(), timing(104), 0L);
        assertTrue(lease.blocksRestoration(109L), "renewed danger must invalidate the old safe countdown");

        lease.observeSafe(ProtectionHoldLease.SafeEvidence.clean(), timing(105), 0L);
        assertEquals(113L, lease.releaseNotBeforeServerTick());
        assertTrue(lease.blocksRestoration(112L));
        assertFalse(lease.blocksRestoration(113L));
    }

    @Test
    void popGenerationChangeInvalidatesAnExistingReleaseCountdown() {
        ProtectionHoldLease lease = new ProtectionHoldLease();
        lease.require(ProtectionHoldLease.ProtectionRequirement.lethalThreat(), timing(100), 4L);
        lease.observeSafe(ProtectionHoldLease.SafeEvidence.clean(), timing(101), 4L);
        assertEquals(109L, lease.releaseNotBeforeServerTick());

        lease.observeSafe(ProtectionHoldLease.SafeEvidence.clean(), timing(102), 5L);
        assertTrue(lease.blocksRestoration(109L),
            "a new protection-consumption generation must invalidate stale pre-pop release evidence");
        assertEquals(110L, lease.releaseNotBeforeServerTick());
    }

    @Test
    void unresolvedEvidenceNeverStartsAReleaseCountdown() {
        ProtectionHoldLease lease = new ProtectionHoldLease();
        lease.require(ProtectionHoldLease.ProtectionRequirement.lethalThreat(), timing(100), 0L);

        lease.observeSafe(new ProtectionHoldLease.SafeEvidence(true, true, false, true, true, true), timing(101), 0L);

        assertEquals(Long.MAX_VALUE, lease.releaseNotBeforeServerTick());
        assertTrue(lease.blocksRestoration(1_000_000L));
    }

    private static TimingSnapshot timing(long tick) {
        return new TimingSnapshot(tick, 100.0d, 10.0d, new TickWindow(tick + 2L, tick + 2L));
    }
}
