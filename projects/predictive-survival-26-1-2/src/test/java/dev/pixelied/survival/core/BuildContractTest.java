package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuildContractTest {
    @Test
    void modIdIsStable() {
        assertEquals("predictive_survival", ModConstants.MOD_ID);
    }
}
