package dev.adrien.spearclient.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SpearControllerPolicyTest {
    @Test
    void kineticOneTapTakesPriorityWhenAvailable() {
        assertEquals(
            SpearControllerPolicy.Action.ONE_TAP,
            SpearControllerPolicy.choose(true, true, true, true, true)
        );
    }

    @Test
    void legitimateLungeBeatsReachWhenOneTapCannotRun() {
        assertEquals(
            SpearControllerPolicy.Action.LUNGE,
            SpearControllerPolicy.choose(true, true, true, false, true)
        );
    }

    @Test
    void reachIsFallbackWhenOneTapAndLungeCannotRun() {
        assertEquals(
            SpearControllerPolicy.Action.REACH,
            SpearControllerPolicy.choose(true, true, true, false, false)
        );
    }

    @Test
    void disabledModulesPreserveVanillaAttack() {
        assertEquals(
            SpearControllerPolicy.Action.VANILLA,
            SpearControllerPolicy.choose(false, false, false, false, false)
        );
    }
}
