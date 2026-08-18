package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HurtWindowProcessorTest {
    @Test
    void strongerHitInsideProtectedWindowOnlyPassesTheDelta() {
        var state = new HurtWindowState(15, 18.0f);
        var decision = HurtWindowProcessor.evaluate(state, 30.0f);

        assertTrue(decision.accepted());
        assertEquals(12.0f, decision.damageForMitigation(), 0.0001f);
        assertEquals(30.0f, decision.nextState().lastHurt(), 0.0001f);
        assertEquals(15, decision.nextState().invulnerableTime());
    }

    @Test
    void equalOrWeakerHitInsideProtectedWindowIsRejected() {
        var state = new HurtWindowState(15, 18.0f);
        var decision = HurtWindowProcessor.evaluate(state, 18.0f);

        assertFalse(decision.accepted());
        assertEquals(0.0f, decision.damageForMitigation(), 0.0001f);
        assertEquals(state, decision.nextState());
    }

    @Test
    void freshHitStoresIncomingAndStartsTwentyTicks() {
        var decision = HurtWindowProcessor.evaluate(new HurtWindowState(0, 0.0f), 11.0f);

        assertTrue(decision.accepted());
        assertEquals(11.0f, decision.damageForMitigation(), 0.0001f);
        assertEquals(new HurtWindowState(20, 11.0f), decision.nextState());
    }

    @Test
    void tickingClampsImmunityAtZeroWithoutClearingLastHurt() {
        var state = new HurtWindowState(3, 18.0f);

        assertEquals(new HurtWindowState(0, 18.0f), state.tick(5));
    }
}
