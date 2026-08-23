package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptureTickClockTest {
    @Test
    void repeatedCaptureOfSamePlayerTickDoesNotAdvanceLogicalTime() {
        CaptureTickClock clock = new CaptureTickClock();

        assertTrue(clock.observe(100));
        assertEquals(1, clock.clientTick());
        assertFalse(clock.observe(100));
        assertEquals(1, clock.clientTick());
        assertTrue(clock.observe(101));
        assertEquals(2, clock.clientTick());
        assertFalse(clock.observe(101));
        assertEquals(2, clock.clientTick());
    }

    @Test
    void resetTreatsNextObservedPlayerTickAsFreshWithoutReusingOldSessionState() {
        CaptureTickClock clock = new CaptureTickClock();
        clock.observe(900);
        clock.observe(901);
        clock.resetObservation();

        assertTrue(clock.observe(0));
        assertEquals(3, clock.clientTick(), "logical time stays monotonic across world/player replacement");
        assertFalse(clock.observe(0));
    }
}
