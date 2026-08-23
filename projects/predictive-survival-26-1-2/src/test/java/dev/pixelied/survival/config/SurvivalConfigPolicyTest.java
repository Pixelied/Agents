package dev.pixelied.survival.config;

import dev.pixelied.survival.planner.SafetyMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalConfigPolicyTest {
    @Test
    void defaultsUseConservativeSmartRescueProfile() {
        SurvivalConfig config = SurvivalConfig.defaults();

        assertEquals(RescueProfile.CONSERVATIVE_SMART, config.rescueProfile());
        assertTrue(config.rescuePolicy().deathProtection());
        assertTrue(config.rescuePolicy().shields());
        assertTrue(config.rescuePolicy().consumables());
        assertTrue(config.rescuePolicy().equipment());
        assertTrue(config.rescuePolicy().inventoryRouting());
        assertTrue(config.rescuePolicy().mainHandTakeover());
        assertTrue(config.rescuePolicy().proactiveDualProtection());
    }

    @Test
    void totemOnlyProfileStrictlyForbidsOtherRescueFamilies() {
        SurvivalConfig config = new SurvivalConfig(
            SafetyMode.SAFE,
            RescueProfile.TOTEM_ONLY,
            RescuePolicy.smartDefaults(),
            true,
            false,
            false,
            false
        );

        RescuePolicy policy = config.rescuePolicy();
        assertTrue(policy.deathProtection());
        assertFalse(policy.shields());
        assertFalse(policy.consumables());
        assertFalse(policy.equipment());
        assertTrue(policy.inventoryRouting());
        assertTrue(policy.mainHandTakeover());
        assertTrue(policy.proactiveDualProtection());
    }

    @Test
    void customProfileUsesExactlyTheCustomPolicy() {
        RescuePolicy custom = new RescuePolicy(true, false, true, false, false, false, false);
        SurvivalConfig config = new SurvivalConfig(
            SafetyMode.BALANCED,
            RescueProfile.CUSTOM,
            custom,
            false,
            false,
            false,
            true
        );

        assertEquals(custom, config.rescuePolicy());
    }
}
