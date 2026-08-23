package dev.pixelied.survival.timing;

import dev.pixelied.survival.core.TickWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContainerPredictionTimingTest {
    @Test
    void correctionSettleWindowIncludesServerArrivalAndReturnPath() {
        TimingSnapshot timing = new TimingSnapshot(
            100,
            200d,
            25d,
            new TickWindow(101, 104)
        );

        // Return path: ceil((RTT / 2 + jitter) / 50ms) + one scheduling tick
        // = ceil(125 / 50) + 1 = 4 ticks. A silent optimistic click is therefore
        // not trusted until the server's latest processing tick (104) + 4 = 108.
        assertEquals(4, timing.serverCorrectionReturnTicks());
        assertEquals(108, timing.containerPredictionSettleTick());

        // A follow-up packet sent only after that settle point must pay the return
        // path plus another conservative outbound path. TimingSnapshot.deadline()
        // already pays the first outbound path, so the route contributes 8 ticks.
        assertEquals(8, timing.containerFollowupRouteTicks());
    }

    @Test
    void zeroLatencyStillWaitsAcrossBothSchedulingBoundaries() {
        TimingSnapshot timing = new TimingSnapshot(
            50,
            0d,
            0d,
            new TickWindow(50, 51)
        );

        assertEquals(1, timing.serverCorrectionReturnTicks());
        assertEquals(52, timing.containerPredictionSettleTick());
        assertEquals(2, timing.containerFollowupRouteTicks());
    }
}
