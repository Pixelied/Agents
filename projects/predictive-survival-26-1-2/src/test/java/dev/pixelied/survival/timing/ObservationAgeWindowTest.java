package dev.pixelied.survival.timing;

import dev.pixelied.survival.core.TickWindow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ObservationAgeWindowTest {
    @Test
    void inboundObservationAgeUsesHalfRttJitterAndSchedulingSafety() {
        TimingSnapshot timing = new TimingSnapshot(100, 200, 25, new TickWindow(101, 104));

        assertEquals(new TickWindow(1, 4), timing.observationAgeWindow());
    }

    @Test
    void zeroLatencyNeverProducesNegativeAge() {
        TimingSnapshot timing = new TimingSnapshot(5, 0, 0, new TickWindow(5, 5));

        assertEquals(new TickWindow(0, 1), timing.observationAgeWindow());
    }
}
