package dev.adrien.crystaloptimizer.v2.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import org.junit.jupiter.api.Test;

final class SelfSurvivalPolicyTest {
    @Test
    void damageBelowComfortCapStillRejectsWhenItWouldKillSelf() {
        SelfDamageEstimate self = new SelfDamageEstimate(10.0f, 0.0f, false);

        SelfSurvivalPolicy.Decision decision = SelfSurvivalPolicy.evaluate(
            self,
            OpportunityIntent.PRESSURE,
            14.0f,
            OptimizerConfig.defaults()
        );

        assertFalse(decision.allowed());
        assertEquals(SelfSurvivalPolicy.Reason.SELF_LETHAL, decision.reason());
    }

    @Test
    void localTotemActivationIsAlwaysRejectedEvenForEnemyLethal() {
        SelfDamageEstimate self = new SelfDamageEstimate(18.0f, 1.0f, true);

        SelfSurvivalPolicy.Decision decision = SelfSurvivalPolicy.evaluate(
            self,
            OpportunityIntent.LETHAL,
            40.0f,
            OptimizerConfig.defaults()
        );

        assertFalse(decision.allowed());
        assertEquals(SelfSurvivalPolicy.Reason.SELF_TOTEM_POP, decision.reason());
    }

    @Test
    void ordinaryLosingTradeIsRejected() {
        SelfDamageEstimate self = new SelfDamageEstimate(8.0f, 12.0f, false);

        SelfSurvivalPolicy.Decision decision = SelfSurvivalPolicy.evaluate(
            self,
            OpportunityIntent.PRESSURE,
            5.0f,
            OptimizerConfig.defaults()
        );

        assertFalse(decision.allowed());
        assertEquals(SelfSurvivalPolicy.Reason.BAD_TRADE, decision.reason());
    }

    @Test
    void lethalSpeedMayExceedComfortCapWhenCertifiedLethalRemainsSafe() {
        SelfDamageEstimate self = new SelfDamageEstimate(13.0f, 7.0f, false);

        SelfSurvivalPolicy.Decision decision = SelfSurvivalPolicy.evaluate(
            self,
            OpportunityIntent.LETHAL,
            20.0f,
            OptimizerConfig.defaults()
        );

        assertTrue(decision.allowed());
        assertEquals(SelfSurvivalPolicy.Reason.ALLOWED, decision.reason());
    }

    @Test
    void lethalEfficiencyRejectsLowValueCrystalSpend() {
        LethalEfficiencyPolicy.Decision decision = LethalEfficiencyPolicy.evaluate(
            new SelfDamageEstimate(1.0f, 19.0f, false),
            OpportunityIntent.PRESSURE,
            3.0f,
            false,
            20.0f,
            OptimizerConfig.defaults()
        );

        assertFalse(decision.allowed());
        assertEquals(LethalEfficiencyPolicy.Reason.LOW_VALUE_SPEND, decision.reason());
    }
}
