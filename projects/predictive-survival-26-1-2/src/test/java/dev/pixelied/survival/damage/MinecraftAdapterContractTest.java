package dev.pixelied.survival.damage;

import net.minecraft.tags.DamageTypeTags;
import org.junit.jupiter.api.Test;

import static dev.pixelied.survival.damage.DamageFlag.BYPASSES_ARMOR;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftAdapterContractTest {
    @Test
    void bypassArmorMapsIntoRuntimeTag() {
        assertEquals(DamageTypeTags.BYPASSES_ARMOR, MinecraftDamageAdapter.tagFor(BYPASSES_ARMOR));
    }

    @Test
    void everyDamageFlagHasExplicitRuntimeTagMapping() {
        for (DamageFlag flag : DamageFlag.values()) {
            assertNotNull(MinecraftDamageAdapter.tagFor(flag), flag.name());
        }
    }

    @Test
    void piercingArrowRequiresPositivePierceLevel() {
        assertFalse(MinecraftDamageAdapter.piercingProjectile(false, 4));
        assertFalse(MinecraftDamageAdapter.piercingProjectile(true, 0));
        assertTrue(MinecraftDamageAdapter.piercingProjectile(true, 1));
    }

    @Test
    void shieldReadinessRespectsFiveTickBoundary() {
        assertFalse(MinecraftBlockingAdapter.snapshot(true, 4, 5, 1f).active());
        assertTrue(MinecraftBlockingAdapter.snapshot(true, 5, 5, 1f).active());
    }

    @Test
    void blockingHelperRejectsImpossibleFractions() {
        assertThrows(IllegalArgumentException.class, () -> MinecraftBlockingAdapter.snapshot(true, 5, 5, 1.1f));
    }
}
