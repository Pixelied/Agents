package dev.adrien.spearclient.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SpearConfigTest {
    @Test
    void defaultsAreConservative() {
        SpearConfig config = SpearConfig.defaults();

        assertFalse(config.oneTap().enabled());
        assertFalse(config.lungeBoost().enabled());
        assertFalse(config.infiniteReach().enabled());
        assertFalse(config.debug());
        assertTrue(config.infiniteReach().teamCheck());
    }
}
