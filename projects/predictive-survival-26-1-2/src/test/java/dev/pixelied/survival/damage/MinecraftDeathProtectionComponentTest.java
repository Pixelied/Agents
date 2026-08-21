package dev.pixelied.survival.damage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftDeathProtectionComponentTest {
    @Test
    void deterministicCustomDeathProtectionEffectsAreCapturedInsteadOfDiscarded() throws Exception {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.DEATH_PROTECTION, new DeathProtection(List.of(
            new ClearAllStatusEffectsConsumeEffect(),
            new ApplyStatusEffectsConsumeEffect(List.of(
                new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0),
                new MobEffectInstance(MobEffects.REGENERATION, 60, 1)
            ))
        )));

        DeathProtectionSnapshot.ProtectionItem item = snapshot(stack);

        assertFalse(item.outcomeUncertain());
        assertTrue(item.clearExistingEffects());
        assertEquals(2, item.effects().size());
        assertEquals("minecraft:fire_resistance", item.effects().get(0).effectKey());
        assertEquals(120, item.effects().get(0).durationTicks());
        assertEquals("minecraft:regeneration", item.effects().get(1).effectKey());
        assertEquals(1, item.effects().get(1).amplifier());
    }

    @Test
    void probabilisticCustomDeathProtectionRemainsExplicitlyUncertain() throws Exception {
        ItemStack stack = new ItemStack(Items.STICK);
        stack.set(DataComponents.DEATH_PROTECTION, new DeathProtection(List.of(
            new ApplyStatusEffectsConsumeEffect(
                new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0),
                0.5f
            )
        )));

        assertTrue(snapshot(stack).outcomeUncertain());
    }

    @SuppressWarnings("unchecked")
    private static DeathProtectionSnapshot.ProtectionItem snapshot(ItemStack stack) throws Exception {
        Method method = MinecraftEquipmentAdapter.class.getDeclaredMethod("protectionItem", ItemStack.class);
        method.setAccessible(true);
        Optional<DeathProtectionSnapshot.ProtectionItem> result =
            (Optional<DeathProtectionSnapshot.ProtectionItem>) method.invoke(null, stack);
        return result.orElseThrow();
    }
}
