package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.DamageRange;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaMobMeleeProfileTest {
    @Test
    void reconstructsKnownServerOnlyBaseMutations() {
        assertEquals(4f, VanillaMobMeleeProfile.reconstructedAttackAttribute(
            "minecraft:wither_skeleton", 2f, false, 1, false), 0.0001f);
        assertEquals(0.5f, VanillaMobMeleeProfile.reconstructedAttackAttribute(
            "minecraft:hoglin", 6f, true, 1, false), 0.0001f);
        assertEquals(6f, VanillaMobMeleeProfile.reconstructedAttackAttribute(
            "minecraft:hoglin", 6f, false, 1, false), 0.0001f);
        assertEquals(4f, VanillaMobMeleeProfile.reconstructedAttackAttribute(
            "minecraft:slime", 1f, false, 4, false), 0.0001f);
        assertEquals(11f, VanillaMobMeleeProfile.reconstructedAttackAttribute(
            "minecraft:phantom", 6f, false, 5, false), 0.0001f);
        assertEquals(8f, VanillaMobMeleeProfile.reconstructedAttackAttribute(
            "minecraft:rabbit", 3f, false, 1, true), 0.0001f);
    }

    @Test
    void specialAttackFormulasMatchVanillaBounds() {
        assertRange(7.5f, 21.5f, VanillaMobMeleeProfile.directDamage(
            "minecraft:iron_golem", 15f, false, 30f));
        assertRange(3f, 8f, VanillaMobMeleeProfile.directDamage(
            "minecraft:hoglin", 6f, false, 30f));
        assertRange(0.5f, 0.5f, VanillaMobMeleeProfile.directDamage(
            "minecraft:hoglin", 0.5f, true, 30f));
        assertRange(5f, 5f, VanillaMobMeleeProfile.directDamage(
            "minecraft:bee", 5.8f, false, 30f));
        assertRange(6f, 6f, VanillaMobMeleeProfile.directDamage(
            "minecraft:magma_cube", 4f, false, 30f));
        assertRange(0f, 0f, VanillaMobMeleeProfile.directDamage(
            "minecraft:creeper", 10f, false, 30f));
    }

    @Test
    void specialFormulaMobsDoNotLeakGenericWeaponOrMaceDamage() {
        DamageRange golem = VanillaMobMeleeProfile.directDamage("minecraft:iron_golem", 15f, false, 50f);
        assertRange(7.5f, 21.5f, golem);
        assertFalse(VanillaMobMeleeProfile.usesGenericItemAttackPipeline("minecraft:iron_golem"));
        assertFalse(VanillaMobMeleeProfile.usesGenericItemAttackPipeline("minecraft:hoglin"));
        assertTrue(VanillaMobMeleeProfile.usesGenericItemAttackPipeline("minecraft:wither_skeleton"));
    }

    @Test
    void genericMaceDamageIncludesDensityAtFallDistance() {
        float damage = VanillaMobMeleeProfile.genericDirectDamage(
            7f,
            1f,
            "minecraft:mace",
            4f,
            4
        );
        // 7 attack attribute + Sharpness-like damage bonus 1 + 14 vanilla smash + 8 Density IV.
        assertEquals(30f, damage, 0.0001f);
    }

    @Test
    void genericMaceDamagePreservesFractionalDoubleFallDistanceUntilFinalDamage() {
        float damage = VanillaMobMeleeProfile.genericDirectDamage(
            7f,
            1f,
            "minecraft:mace",
            4.25d,
            4
        );
        // Vanilla keeps Entity.fallDistance as double: 8 base + 14.5 smash + 8.5 Density IV.
        assertEquals(31f, damage, 0.0001f);
    }

    private static void assertRange(float min, float max, DamageRange actual) {
        assertEquals(min, actual.min(), 0.0001f);
        assertEquals(max, actual.max(), 0.0001f);
    }
}
