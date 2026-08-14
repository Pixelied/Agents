package dev.adrien.spearclient.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MovementEnvelopeTest {
    @Test
    void regularFirstFiveMatchServerFormulaWithZeroExpectedVelocity() {
        assertEquals(10.0, MovementEnvelope.maxDistanceFromTickOrigin(false, 1, 0.0), 1e-9);
        assertEquals(Math.sqrt(200.0), MovementEnvelope.maxDistanceFromTickOrigin(false, 2, 0.0), 1e-9);
        assertEquals(Math.sqrt(300.0), MovementEnvelope.maxDistanceFromTickOrigin(false, 3, 0.0), 1e-9);
        assertEquals(20.0, MovementEnvelope.maxDistanceFromTickOrigin(false, 4, 0.0), 1e-9);
        assertEquals(Math.sqrt(500.0), MovementEnvelope.maxDistanceFromTickOrigin(false, 5, 0.0), 1e-9);
    }

    @Test
    void packetCountAboveFiveFallsBackToOnePacketAllowance() {
        assertEquals(10.0, MovementEnvelope.maxDistanceFromTickOrigin(false, 6, 0.0), 1e-9);
    }

    @Test
    void fallFlyingUsesThreeHundredMeterSquaredAllowancePerPacket() {
        assertEquals(Math.sqrt(1500.0), MovementEnvelope.maxDistanceFromTickOrigin(true, 5, 0.0), 1e-9);
    }

    @Test
    void conservativeBudgetIncludesNineBlocksAndRejectsAnythingBeyondIt() {
        assertTrue(MovementEnvelope.isInsideConservativeBudget(9.0));
        assertFalse(MovementEnvelope.isInsideConservativeBudget(9.000001));
    }
}
