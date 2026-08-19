package dev.pixelied.survival.damage;

import org.junit.jupiter.api.Test;

import static dev.pixelied.survival.damage.DamageFlag.BYPASSES_INVULNERABILITY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathProtectionTest {
    private final DamageSimulator simulator = new DamageSimulator();

    @Test
    void mainHandDeathProtectionPreventsDeath() {
        DamageResult result = simulator.simulate(
            DamageSimulatorMitigationTest.player(4f, 0f, MitigationSnapshot.none(), StatusEffectsSnapshot.none(),
                DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()), HurtState.unknown()),
            source(8f)
        );

        assertTrue(result.deathProtectionConsumed());
        assertEquals(1f, result.after().health(), 0.0001f);
        assertFalse(result.after().deathProtection().mainHandAvailable());
    }

    @Test
    void offHandDeathProtectionPreventsDeath() {
        DamageResult result = simulator.simulate(
            DamageSimulatorMitigationTest.player(4f, 0f, MitigationSnapshot.none(), StatusEffectsSnapshot.none(),
                DeathProtectionSnapshot.offHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()), HurtState.unknown()),
            source(8f)
        );

        assertTrue(result.deathProtectionConsumed());
        assertEquals(1f, result.after().health(), 0.0001f);
        assertFalse(result.after().deathProtection().offHandAvailable());
    }

    @Test
    void mainHandWinsWhenBothHandsHaveProtection() {
        DeathProtectionSnapshot.ProtectionItem main = new DeathProtectionSnapshot.ProtectionItem(
            false, java.util.List.of(new EffectInstanceSnapshot("test:main", 40, 0)));
        DeathProtectionSnapshot.ProtectionItem off = new DeathProtectionSnapshot.ProtectionItem(
            false, java.util.List.of(new EffectInstanceSnapshot("test:off", 40, 0)));

        DamageResult result = simulator.simulate(
            DamageSimulatorMitigationTest.player(4f, 0f, MitigationSnapshot.none(), StatusEffectsSnapshot.none(),
                DeathProtectionSnapshot.both(main, off), HurtState.unknown()),
            source(8f)
        );

        assertFalse(result.after().deathProtection().mainHandAvailable());
        assertTrue(result.after().deathProtection().offHandAvailable());
        assertTrue(result.after().statusEffects().effect("test:main").isPresent());
        assertTrue(result.after().statusEffects().effect("test:off").isEmpty());
    }

    @Test
    void bypassInvulnerabilityPreventsDeathProtection() {
        DamageResult result = simulator.simulate(
            DamageSimulatorMitigationTest.player(4f, 0f, MitigationSnapshot.none(), StatusEffectsSnapshot.none(),
                DeathProtectionSnapshot.offHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()), HurtState.unknown()),
            source(8f, BYPASSES_INVULNERABILITY)
        );

        assertFalse(result.deathProtectionConsumed());
        assertEquals(0f, result.after().health(), 0.0001f);
        assertTrue(result.after().deathProtection().offHandAvailable());
    }

    @Test
    void vanillaTotemAppliesSourceConfirmedEffectsAndAbsorption() {
        DamageResult result = simulator.simulate(
            DamageSimulatorMitigationTest.player(4f, 0f, MitigationSnapshot.none(), new StatusEffectsSnapshot(false, 3),
                DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()), HurtState.unknown()),
            source(25f)
        );

        assertEquals(1f, result.after().health(), 0.0001f);
        assertEquals(8f, result.after().absorption(), 0.0001f);
        assertEquals(900, result.after().statusEffects().effect("minecraft:regeneration").orElseThrow().durationTicks());
        assertEquals(1, result.after().statusEffects().effect("minecraft:regeneration").orElseThrow().amplifier());
        assertEquals(100, result.after().statusEffects().effect("minecraft:absorption").orElseThrow().durationTicks());
        assertEquals(1, result.after().statusEffects().effect("minecraft:absorption").orElseThrow().amplifier());
        assertEquals(800, result.after().statusEffects().effect("minecraft:fire_resistance").orElseThrow().durationTicks());
        assertEquals(0, result.after().statusEffects().effect("minecraft:fire_resistance").orElseThrow().amplifier());
        assertTrue(result.after().statusEffects().fireResistance());
        assertEquals(-1, result.after().statusEffects().resistanceAmplifier());
    }

    private static DamageSourceSnapshot source(float raw, DamageFlag... flags) {
        return new DamageSourceSnapshot(
            dev.pixelied.survival.core.DamageRange.exact(raw), java.util.Set.of(flags), false, 1f, false,
            java.util.Optional.empty(), "test"
        );
    }
}
