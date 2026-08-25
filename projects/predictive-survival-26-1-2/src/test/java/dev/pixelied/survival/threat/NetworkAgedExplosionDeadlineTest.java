package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NetworkAgedExplosionDeadlineTest {
    @Test
    void observedFuseIsAgedBeforePlannerDeadline() {
        TimingSnapshot timing = new TimingSnapshot(100, 200, 25, new TickWindow(101, 104));
        TickWindow age = timing.observationAgeWindow();

        TickWindow serverFuse = ExplosionTiming.ageCountdown(5, age);

        assertEquals(new TickWindow(1, 4), serverFuse);
        assertTrue(serverFuse.earliest() < 5);
    }

    @Test
    void countdownSaturatesAtImmediateInsteadOfGoingNegative() {
        assertEquals(
            new TickWindow(0, 1),
            ExplosionTiming.ageCountdown(2, new TickWindow(1, 5))
        );
    }
}
