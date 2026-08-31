package dev.pixelied.survival.timing;

import dev.pixelied.survival.core.TickWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerTimingEstimatorTest {
    @Test
    void shieldNeedsPacketArrivalPlusFiveServerTicks() {
        TimingSnapshot snapshot = new TimingSnapshot(100, 100, 10, new TickWindow(102, 103));

        assertFalse(snapshot.canCompleteBefore(5, new TickWindow(106, 106)));
        assertTrue(snapshot.canCompleteBefore(5, new TickWindow(109, 110)));
    }

    @Test
    void jitterWidensConservativeArrivalWindow() {
        ServerTimingEstimator low = estimatorWithRtts(100, 98, 102, 100);
        ServerTimingEstimator high = estimatorWithRtts(40, 160, 50, 150);

        long lowLatest = low.snapshot(100).nextPacketProcessingWindow().latest();
        long highLatest = high.snapshot(100).nextPacketProcessingWindow().latest();

        assertTrue(highLatest > lowLatest);
    }

    @Test
    void sameLogicalTickRttSampleReplacesInsteadOfOverweightingDirtyReanalysis() {
        ServerTimingEstimator estimator = new ServerTimingEstimator();
        estimator.observeRttMillis(10L, 100);
        estimator.observeRttMillis(10L, 400);
        estimator.observeRttMillis(11L, 100);

        TimingSnapshot snapshot = estimator.snapshot(11L);

        assertEquals(250d, snapshot.rttMs(), 0.0001d);
        assertEquals(150d, snapshot.jitterMs(), 0.0001d);
    }

    @Test
    void legacyRuntimeObservationReplacesAcrossSameTickDirtyReanalysis() {
        ServerTimingEstimator estimator = new ServerTimingEstimator();
        estimator.observeRttMillis(100);
        estimator.snapshot(10L);

        estimator.observeRttMillis(400);
        estimator.snapshot(10L);

        estimator.observeRttMillis(100);
        TimingSnapshot snapshot = estimator.snapshot(11L);

        assertEquals(250d, snapshot.rttMs(), 0.0001d);
        assertEquals(150d, snapshot.jitterMs(), 0.0001d);
    }

    @Test
    void oldRttOutlierRollsOutOfBoundedSampleWindow() {
        ServerTimingEstimator estimator = new ServerTimingEstimator();
        estimator.observeRttMillis(1000);
        for (int i = 0; i < 24; i++) estimator.observeRttMillis(100);

        assertTrue(estimator.snapshot(100).jitterMs() < 100);
    }

    @Test
    void slowClientTickNeverShrinksServerArrivalBudget() {
        ServerTimingEstimator normal = new ServerTimingEstimator();
        normal.observeRttMillis(200);
        normal.observeClientTickNanos(50_000_000L);

        ServerTimingEstimator stalled = new ServerTimingEstimator();
        stalled.observeRttMillis(200);
        stalled.observeClientTickNanos(100_000_000L);

        assertTrue(
            stalled.snapshot(100).nextPacketProcessingWindow().latest()
                >= normal.snapshot(100).nextPacketProcessingWindow().latest()
        );
    }

    @Test
    void resetDiscardsPriorServerTimingSamples() {
        ServerTimingEstimator estimator = new ServerTimingEstimator();
        for (int i = 0; i < 20; i++) estimator.observeRttMillis(20);
        estimator.observeClientTickNanos(5_000_000L);

        estimator.reset();
        TimingSnapshot fresh = estimator.snapshot(100);

        assertEquals(250d, fresh.rttMs(), 0.0001d);
        assertEquals(100d, fresh.jitterMs(), 0.0001d);
    }

    @Test
    void deadlineUsesSameMechanismForAnyRequiredServerTicks() {
        TimingSnapshot snapshot = new TimingSnapshot(100, 100, 10, new TickWindow(102, 103));

        Deadline shield = snapshot.deadline(5);
        assertFalse(shield.completesBefore(new TickWindow(107, 107)));
        assertTrue(shield.completesBefore(new TickWindow(108, 109)));
    }

    @Test
    void invalidTimingInputsAreRejected() {
        ServerTimingEstimator estimator = new ServerTimingEstimator();
        assertThrows(IllegalArgumentException.class, () -> estimator.observeRttMillis(-1));
        assertThrows(IllegalArgumentException.class, () -> estimator.observeClientTickNanos(0));
        assertThrows(IllegalArgumentException.class, () ->
            new TimingSnapshot(100, 100, 10, new TickWindow(102, 103)).canCompleteBefore(-1, new TickWindow(110, 110)));
    }

    private static ServerTimingEstimator estimatorWithRtts(int... rtts) {
        ServerTimingEstimator estimator = new ServerTimingEstimator();
        estimator.observeClientTickNanos(50_000_000L);
        for (int rtt : rtts) estimator.observeRttMillis(rtt);
        return estimator;
    }
}
