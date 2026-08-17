package dev.adrien.crystaloptimizer;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class BootstrapSmokeTest {
    @Test
    void loadsMinecraft26_1_2Classes() {
        assertDoesNotThrow(() -> {
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        });
        assertEquals("crystaloptimizer", CrystalOptimizer.MOD_ID);
    }
}
