package dev.pixelied.survival.damage;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.ClearAllStatusEffectsConsumeEffect;
import net.minecraft.world.item.consume_effects.PlaySoundConsumeEffect;
import net.minecraft.world.item.consume_effects.RemoveStatusEffectsConsumeEffect;

import java.util.List;
import java.util.Optional;

/** Converts client-visible 26.1.2 DEATH_PROTECTION consume effects into conservative simulation state. */
public final class MinecraftDeathProtectionAdapter {
    private MinecraftDeathProtectionAdapter() {
    }

    public static Optional<DeathProtectionSnapshot.ProtectionItem> snapshot(ItemStack stack) {
        if (stack == null) throw new NullPointerException("stack");
        DeathProtection component = stack.get(DataComponents.DEATH_PROTECTION);
        if (component == null) return Optional.empty();
        if (component.equals(DeathProtection.TOTEM_OF_UNDYING)) {
            return Optional.of(DeathProtectionSnapshot.ProtectionItem.vanillaTotem());
        }

        DeathProtectionEffectAccumulator accumulator = new DeathProtectionEffectAccumulator();

        for (var consumeEffect : component.deathEffects()) {
            if (consumeEffect instanceof ClearAllStatusEffectsConsumeEffect) {
                accumulator.clearAllStatusEffects();
                continue;
            }
            if (consumeEffect instanceof PlaySoundConsumeEffect) {
                continue;
            }
            if (consumeEffect instanceof ApplyStatusEffectsConsumeEffect apply) {
                if (Float.compare(apply.probability(), 1f) != 0 || !accumulator.hasKnownEmptyEffectBase()) {
                    accumulator.markStatusOutcomeUncertain();
                    continue;
                }
                List<EffectInstanceSnapshot> knownEffects = apply.effects().stream()
                    .map(effect -> new EffectInstanceSnapshot(
                        effect.getEffect().getRegisteredName(),
                        effect.getDuration(),
                        effect.getAmplifier()
                    ))
                    .toList();
                accumulator.addKnownStatusEffects(knownEffects);
                continue;
            }
            if (consumeEffect instanceof RemoveStatusEffectsConsumeEffect) {
                accumulator.removeStatusEffects();
                continue;
            }

            // Teleport and any future non-status consume effect can alter position or another
            // survival-relevant state. A later status clear cannot make that outcome known.
            accumulator.markNonStatusOutcomeUncertain();
        }

        return Optional.of(accumulator.snapshot());
    }
}
