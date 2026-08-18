package dev.adrien.crystaloptimizer.execution;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RotationMathTest {
    @Test
    void adaptiveRotationIsBoundedNormallyAndSnapsWhenCommitted() {
        RotationStep normal = RotationMath.next(
            170.0f,
            10.0f,
            -170.0f,
            -20.0f,
            RotationMode.ADAPTIVE,
            false,
            5.0f
        );
        RotationStep committed = RotationMath.next(
            170.0f,
            10.0f,
            -170.0f,
            -20.0f,
            RotationMode.ADAPTIVE,
            true,
            5.0f
        );

        assertEquals(175.0f, normal.yaw(), 0.0001f);
        assertEquals(5.0f, normal.pitch(), 0.0001f);
        assertEquals(-170.0f, committed.yaw(), 0.0001f);
        assertEquals(-20.0f, committed.pitch(), 0.0001f);
    }

    @Test
    void smoothRotationUsesShortestWrappedYawPath() {
        RotationStep step = RotationMath.next(
            -179.0f,
            0.0f,
            179.0f,
            0.0f,
            RotationMode.SMOOTH,
            true,
            1.0f
        );

        assertEquals(180.0f, step.yaw(), 0.0001f);
    }

    @Test
    void instantRealAlwaysUsesRequestedVisibleAngles() {
        RotationStep step = RotationMath.next(
            0.0f,
            0.0f,
            92.0f,
            35.0f,
            RotationMode.INSTANT_REAL,
            false,
            2.0f
        );

        assertEquals(92.0f, step.yaw(), 0.0001f);
        assertEquals(35.0f, step.pitch(), 0.0001f);
    }
}
