package dev.adrien.crystaloptimizer.intel;

import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteCombatObservationPolicyTest {
    @Test
    void activeRemoteHurtWindowNeverInventsServerLastHurt() {
        HurtWindowState state = RemoteCombatObservationPolicy.hurtWindow(17);

        assertEquals(17, state.invulnerableTime());
        assertFalse(state.lastHurtKnown());
    }

    @Test
    void expiredRemoteHurtWindowHasNoRelevantHiddenThreshold() {
        HurtWindowState state = RemoteCombatObservationPolicy.hurtWindow(0);

        assertEquals(0, state.invulnerableTime());
        assertTrue(state.lastHurtKnown());
        assertEquals(0.0f, state.lastHurt());
    }

    @Test
    void absorptionIsOnlyAnUpperBoundDerivedFromSynchronizedEffect() {
        EffectState effects = new EffectState(
            Optional.empty(),
            Optional.empty(),
            Optional.of(new EffectState.EffectInstance(1, 80)),
            Optional.empty()
        );

        assertEquals(8.0f, RemoteCombatObservationPolicy.absorptionUpperBound(effects));
        assertEquals(0.0f, RemoteCombatObservationPolicy.absorptionUpperBound(EffectState.empty()));
    }

    @Test
    void onlyVisibleHandTotemsBecomeExactCombatState() {
        assertEquals(TotemState.NONE, RemoteCombatObservationPolicy.visibleTotem(false, false));
        assertEquals(TotemState.MAINHAND, RemoteCombatObservationPolicy.visibleTotem(true, false));
        assertEquals(TotemState.OFFHAND, RemoteCombatObservationPolicy.visibleTotem(false, true));
        assertEquals(TotemState.BOTH, RemoteCombatObservationPolicy.visibleTotem(true, true));
    }
}
