package dev.adrien.spearclient.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpearControllerPolicyTest {
    @Test
    void kineticOneTapTakesPriorityWhenAvailable() {
        assertEquals(
            SpearControllerPolicy.Action.ONE_TAP,
            SpearControllerPolicy.choose(true, true, true)
        );
    }

    @Test
    void reachIsFallbackWhenOneTapCannotRun() {
        assertEquals(
            SpearControllerPolicy.Action.REACH,
            SpearControllerPolicy.choose(true, true, false)
        );
    }

    @Test
    void disabledModulesPreserveVanillaAttack() {
        assertEquals(
            SpearControllerPolicy.Action.VANILLA,
            SpearControllerPolicy.choose(false, false, false)
        );
    }
}
