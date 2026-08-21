package dev.adrien.crystaloptimizer.v2.damage;

import dev.adrien.crystaloptimizer.sim.damage.DamageRequest;
import dev.adrien.crystaloptimizer.sim.damage.DamageResult;
import dev.adrien.crystaloptimizer.sim.damage.VanillaDamageSimulator;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DamageEngineProgressiveDamageTest {
    @Test
    void progressiveProjectionKeepsUnitsExplicitAcrossProtectedAbsorptionAndTotemHits() {
        DamageProjection unprotected = project(SimCombatant.testPlayer(20.0f), 12.0f);
        assertEquals(12.0f, unprotected.postMitigationIncoming(), 0.0001f);
        assertEquals(12.0f, unprotected.healthLoss(), 0.0001f);
        assertEquals(12.0f, unprotected.effectiveTotalLoss(), 0.0001f);
        assertEquals(12.0f, unprotected.nextHurtThreshold(), 0.0001f);

        SimCombatant protectedVictim = SimCombatant.testPlayer(20.0f)
            .withHurtWindow(new HurtWindowState(15, 10.0f));
        DamageProjection weakerProtected = project(protectedVictim, 8.0f);
        assertEquals(8.0f, weakerProtected.postMitigationIncoming(), 0.0001f);
        assertEquals(0.0f, weakerProtected.effectiveTotalLoss(), 0.0001f);
        assertEquals(10.0f, weakerProtected.nextHurtThreshold(), 0.0001f);

        DamageProjection strongerProtected = project(protectedVictim, 12.0f);
        assertEquals(12.0f, strongerProtected.postMitigationIncoming(), 0.0001f);
        assertEquals(2.0f, strongerProtected.healthLoss(), 0.0001f);
        assertEquals(2.0f, strongerProtected.effectiveTotalLoss(), 0.0001f);
        assertEquals(12.0f, strongerProtected.nextHurtThreshold(), 0.0001f);

        DamageProjection absorptionOnly = project(
            SimCombatant.testPlayer(20.0f).withAbsorption(8.0f),
            6.0f
        );
        assertEquals(6.0f, absorptionOnly.absorptionLoss(), 0.0001f);
        assertEquals(0.0f, absorptionOnly.healthLoss(), 0.0001f);
        assertEquals(6.0f, absorptionOnly.effectiveTotalLoss(), 0.0001f);
        assertEquals(22.0f, absorptionOnly.postHitEffectiveHealth(), 0.0001f);

        DamageProjection totemPop = project(
            SimCombatant.testPlayer(5.0f).withTotem(TotemState.OFFHAND),
            10.0f
        );
        assertTrue(totemPop.totemTriggered());
        assertEquals(10.0f, totemPop.healthLoss(), 0.0001f);
        assertEquals(9.0f, totemPop.postHitEffectiveHealth(), 0.0001f);

        for (DamageProjection projection : new DamageProjection[] {
            unprotected,
            weakerProtected,
            strongerProtected,
            absorptionOnly,
            totemPop
        }) {
            assertTrue(projection.healthLoss() >= 0.0f);
            assertTrue(projection.absorptionLoss() >= 0.0f);
            assertTrue(
                projection.effectiveTotalLoss() <= projection.postMitigationIncoming() + 0.0001f,
                "effective loss must never exceed the pre-hurt-window incoming amount"
            );
        }
    }

    private static DamageProjection project(SimCombatant victim, float rawIncoming) {
        DamageResult result = VanillaDamageSimulator.apply(victim, DamageRequest.explosion(rawIncoming));
        float nextHurtThreshold = result.target().hurtWindow().lastHurtKnown()
            ? result.target().hurtWindow().lastHurt()
            : 0.0f;
        return new DamageProjection(
            result.trace().rawIncoming(),
            result.trace().incoming(),
            result.trace().absorptionConsumed(),
            result.trace().healthDamage(),
            result.target().health() + result.target().absorption(),
            nextHurtThreshold,
            result.trace().totemTriggered()
        );
    }
}
