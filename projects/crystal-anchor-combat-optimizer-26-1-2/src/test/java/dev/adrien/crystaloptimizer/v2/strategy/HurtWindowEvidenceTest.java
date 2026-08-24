package dev.adrien.crystaloptimizer.v2.strategy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HurtWindowEvidenceTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-00000000c401");

    @Test
    void ambiguousRemoteWindowStaysBoundedNotExact() {
        HurtWindowTracker tracker = new HurtWindowTracker();
        long now = 1_000_000_000L;
        tracker.observeEvidence(
            TARGET,
            DamageWindowEvidence.bounded(6.0f, 9.0f, 12.0f, 16, now)
        );

        HurtThresholdEstimate estimate = tracker.estimate(TARGET, 15, now + 20_000_000L);
        assertFalse(estimate.exact());
        assertEquals(6.0f, estimate.lowerBound(), 0.001f);
        assertEquals(9.0f, estimate.expected(), 0.001f);
        assertEquals(12.0f, estimate.upperBound(), 0.001f);
        assertTrue(estimate.confidence() > 0.0 && estimate.confidence() < 1.0);
    }

    @Test
    void exactEvidenceStaysExactInsideWindow() {
        HurtWindowTracker tracker = new HurtWindowTracker();
        long now = 2_000_000_000L;
        tracker.observeEvidence(TARGET, DamageWindowEvidence.exact(8.0f, 16, now));

        HurtThresholdEstimate estimate = tracker.estimate(TARGET, 15, now + 20_000_000L);
        assertTrue(estimate.exact());
        assertEquals(8.0f, estimate.expected(), 0.001f);
    }

    @Test
    void staleEvidenceFallsBackToUnknownProtected() {
        HurtWindowTracker tracker = new HurtWindowTracker();
        long now = 3_000_000_000L;
        tracker.observeEvidence(TARGET, DamageWindowEvidence.exact(8.0f, 16, now));

        HurtThresholdEstimate estimate = tracker.estimate(TARGET, 15, now + 2_000_000_000L);
        assertEquals(0.0, estimate.confidence(), 0.0001);
        assertEquals(HurtThresholdEstimate.MAX_CRYSTAL_HARD_INCOMING, estimate.upperBound(), 0.001f);
    }
}
