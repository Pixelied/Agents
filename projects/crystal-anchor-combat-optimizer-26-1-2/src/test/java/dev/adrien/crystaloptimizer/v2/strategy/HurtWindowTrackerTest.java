package dev.adrien.crystaloptimizer.v2.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

final class HurtWindowTrackerTest {
    @Test
    void exactEvidenceRemainsExactWhileObservedWindowIsProtected() {
        HurtWindowTracker tracker = new HurtWindowTracker();
        UUID target = UUID.randomUUID();
        tracker.observeEvidence(target, DamageWindowEvidence.exact(18.0f, 20, 1_000L));

        HurtThresholdEstimate estimate = tracker.estimate(target, 15, 1_050L);
        assertEquals(18.0f, estimate.lowerBound(), 1.0e-5f);
        assertEquals(18.0f, estimate.expected(), 1.0e-5f);
        assertEquals(18.0f, estimate.upperBound(), 1.0e-5f);
        assertEquals(1.0, estimate.confidence());
    }

    @Test
    void unknownProtectedWindowDoesNotPretendToKnowThreshold() {
        HurtThresholdEstimate estimate = new HurtWindowTracker().estimate(
            UUID.randomUUID(),
            15,
            2_000L
        );

        assertEquals(0.0, estimate.confidence());
        assertTrue(estimate.upperBound() > estimate.lowerBound());
    }

    @Test
    void expiredWindowReturnsZeroThreshold() {
        HurtWindowTracker tracker = new HurtWindowTracker();
        UUID target = UUID.randomUUID();
        tracker.observeEvidence(target, DamageWindowEvidence.exact(20.0f, 20, 1_000L));

        HurtThresholdEstimate estimate = tracker.estimate(target, 10, 2_000L);
        assertEquals(0.0f, estimate.upperBound(), 1.0e-5f);
        assertEquals(1.0, estimate.confidence());
    }
}
