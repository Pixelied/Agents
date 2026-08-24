package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HurtWindowUncertaintyTest {
    @Test
    void unknownProtectedThresholdDoesNotPretendIncomingDamageWasAccepted() {
        HurtWindowState observedRemoteWindow = HurtWindowState.unknownThreshold(20);

        HurtWindowDecision decision = HurtWindowProcessor.evaluate(observedRemoteWindow, 12.0f);

        assertFalse(decision.accepted());
        assertTrue(decision.uncertain());
        assertFalse(decision.nextState().lastHurtKnown());
    }

    @Test
    void freshHitAfterProtectedHalfWindowEstablishesKnownThreshold() {
        HurtWindowState observedRemoteWindow = HurtWindowState.unknownThreshold(10);

        HurtWindowDecision decision = HurtWindowProcessor.evaluate(observedRemoteWindow, 12.0f);

        assertTrue(decision.accepted());
        assertFalse(decision.uncertain());
        assertTrue(decision.nextState().lastHurtKnown());
    }

    @Test
    void exactLocallyTrackedThresholdRemainsExact() {
        HurtWindowState exact = new HurtWindowState(20, 9.0f);

        HurtWindowDecision decision = HurtWindowProcessor.evaluate(exact, 13.0f);

        assertTrue(decision.accepted());
        assertFalse(decision.uncertain());
        assertTrue(decision.nextState().lastHurtKnown());
    }

    @Test
    void damageSimulatorSurfacesUnknownThresholdInsteadOfInventingDamage() {
        SimCombatant target = SimCombatant.testPlayer(20.0f)
            .withHurtWindow(HurtWindowState.unknownThreshold(20));

        DamageResult result = VanillaDamageSimulator.apply(target, DamageRequest.explosion(12.0f));

        assertFalse(result.accepted());
        assertTrue(result.uncertain());
        assertFalse(result.target().hurtWindow().lastHurtKnown());
    }
}
