package dev.adrien.spearclient.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KineticDamageModelTest {
    @Test
    void knownMovementIsScaledByTwentyForRelativeSpeed() {
        assertEquals(120.0, KineticDamageModel.relativeSpeed(6.0, 0.0), 1e-9);
    }

    @Test
    void targetForwardMotionCannotProduceNegativeRelativeSpeed() {
        assertEquals(0.0, KineticDamageModel.relativeSpeed(2.0, 3.0), 1e-9);
    }

    @Test
    void rawDamageUsesFlooredSpeedContribution() {
        assertEquals(181.0, KineticDamageModel.rawDamage(1.0, 1.5, 120.0), 1e-9);
    }

    @Test
    void requiredKnownMovementInvertsTheUnflooredThresholdModel() {
        assertEquals(71.0 / 1.5 / 20.0,
            KineticDamageModel.requiredKnownMovement(72.0, 1.0, 1.5), 1e-9);
    }
}
