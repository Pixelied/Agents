package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.TickWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerHurtStateTrackerTest {
    @Test
    void unexpectedHealthLossInvalidatesRawLastHurt() {
        ServerHurtStateTracker tracker = new ServerHurtStateTracker();
        tracker.recordPredictedApplied(12f, new TickWindow(50, 50));
        tracker.recordObservedHealthDelta(3f, new TickWindow(55, 55));

        assertEquals(Confidence.UNKNOWN, tracker.current().confidence());
        assertEquals(0f, tracker.conservativeForLethalDecision().lastHurt().max(), 0.0001f);
    }

    @Test
    void exactPredictionStartsExactTwentyTickState() {
        ServerHurtStateTracker tracker = new ServerHurtStateTracker();
        tracker.recordPredictedApplied(12f, new TickWindow(50, 50));

        assertEquals(Confidence.EXACT, tracker.current().confidence());
        assertEquals(12f, tracker.current().lastHurt().max(), 0.0001f);
        assertEquals(20, tracker.current().invulnerableTime());
    }

    @Test
    void matchingObservationPromotesToMatchedWithoutReplacingRawDamage() {
        ServerHurtStateTracker tracker = new ServerHurtStateTracker();
        tracker.recordPredictedApplied(12f, new TickWindow(50, 50));
        tracker.recordObservedHealthDelta(3f, new TickWindow(50, 50));

        assertEquals(Confidence.MATCHED, tracker.current().confidence());
        assertEquals(12f, tracker.current().lastHurt().max(), 0.0001f);
    }

    @Test
    void boundedApplicationWindowGetsNoLethalIframeCreditUntilMatched() {
        ServerHurtStateTracker tracker = new ServerHurtStateTracker();
        tracker.recordPredictedApplied(12f, new TickWindow(50, 52));

        assertEquals(Confidence.BOUNDED, tracker.current().confidence());
        assertEquals(0f, tracker.conservativeForLethalDecision().lastHurt().max(), 0.0001f);
    }

    @Test
    void conservativeCreditEndsAtStrongCooldownBoundary() {
        ServerHurtStateTracker tracker = new ServerHurtStateTracker();
        tracker.recordPredictedApplied(12f, new TickWindow(50, 50));

        tracker.tick(9);
        assertEquals(11, tracker.current().invulnerableTime());
        assertEquals(12f, tracker.conservativeForLethalDecision().lastHurt().max(), 0.0001f);

        tracker.tick(1);
        assertEquals(10, tracker.current().invulnerableTime());
        assertEquals(0f, tracker.conservativeForLethalDecision().lastHurt().max(), 0.0001f);
    }

    @Test
    void observedHealthLossWithoutPredictionNeverInventsRawLastHurt() {
        ServerHurtStateTracker tracker = new ServerHurtStateTracker();
        tracker.recordObservedHealthDelta(6f, new TickWindow(70, 70));

        assertEquals(Confidence.UNKNOWN, tracker.current().confidence());
        assertEquals(0f, tracker.current().lastHurt().max(), 0.0001f);
    }
}
