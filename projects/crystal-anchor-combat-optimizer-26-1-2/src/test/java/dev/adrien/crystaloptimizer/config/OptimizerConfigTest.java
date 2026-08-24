package dev.adrien.crystaloptimizer.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.crystaloptimizer.execution.RotationMode;
import org.junit.jupiter.api.Test;

final class OptimizerConfigTest {
    @Test
    void defaultsAreLethalSpeedAndValidationRejectsBadRange() {
        OptimizerConfig config = OptimizerConfig.defaults();
        assertEquals(OptimizerStrategy.LETHAL_SPEED, config.strategy());
        assertEquals(RotationMode.ADAPTIVE, config.rotationMode());
        assertTrue(config.crystals());
        assertTrue(config.anchors());
        assertTrue(config.autoRestock());
        assertFalse(config.enabled());
        assertTrue(config.withEnabled(true).enabled());

        OptimizerConfig invalid = new OptimizerConfig(
            true,
            OptimizerStrategy.LETHAL_SPEED,
            0.5,
            4.0f,
            12.0f,
            8.0f,
            true,
            true,
            true,
            RotationMode.ADAPTIVE,
            true
        );
        assertThrows(IllegalArgumentException.class, invalid::validated);
    }
}
