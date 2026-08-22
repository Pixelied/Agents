package dev.pixelied.survival.damage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathProtectionEffectAccumulatorTest {
    @Test
    void laterClearAllErasesEarlierStatusOutcomeUncertaintyInVanillaOrder() {
        DeathProtectionEffectAccumulator accumulator = new DeathProtectionEffectAccumulator();
        accumulator.markStatusOutcomeUncertain();
        accumulator.clearAllStatusEffects();

        DeathProtectionSnapshot.ProtectionItem item = accumulator.snapshot();
        assertTrue(item.clearExistingEffects());
        assertTrue(item.effects().isEmpty());
        assertFalse(item.outcomeUncertain());
    }

    @Test
    void laterStatusClearDoesNotEraseEarlierNonStatusUncertainty() {
        DeathProtectionEffectAccumulator accumulator = new DeathProtectionEffectAccumulator();
        accumulator.markNonStatusOutcomeUncertain();
        accumulator.clearAllStatusEffects();

        DeathProtectionSnapshot.ProtectionItem item = accumulator.snapshot();
        assertTrue(item.clearExistingEffects());
        assertTrue(item.effects().isEmpty());
        assertTrue(item.outcomeUncertain());
    }
}
