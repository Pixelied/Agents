package dev.pixelied.maceswap;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrajectoryMathTest {
    @Test void strictSmashThresholdAndGlideGuard() {
        assertFalse(TrajectoryMath.maceEligible(1.5, false));
        assertFalse(TrajectoryMath.maceEligible(100, true));
        assertTrue(TrajectoryMath.maceEligible(1.50001, false));
    }
    @Test void downwardOnlyAccumulatesFallDistance() {
        assertEquals(1.6, TrajectoryMath.fallDistanceAfterMovement(1.0, -0.6), 1e-9);
        assertEquals(1.0, TrajectoryMath.fallDistanceAfterMovement(1.0, 0.6), 1e-9);
    }
    @Test void atmosphericVerticalMotion() {
        assertEquals((-0.3 - 0.08) * 0.98, TrajectoryMath.airVelocityY(-0.3, 0.08, 0.98), 1e-8);
    }
    @Test void targetMovementAndLatency() {
        assertEquals(0.0, TrajectoryMath.dampedTargetMotion(2, 0, 0.85), 1e-9);
        assertEquals(1.85, TrajectoryMath.dampedTargetMotion(1, 2, 0.85), 1e-9);
        assertEquals(2, TrajectoryMath.latencyTicks(50, 50));
        assertEquals(4, TrajectoryMath.latencyTicks(260, 50));
    }
}
