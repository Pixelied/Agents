package dev.pixelied.survival.damage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record StatusEffectsSnapshot(
    boolean fireResistance,
    int resistanceAmplifier,
    Map<String, EffectInstanceSnapshot> effects
) {
    public StatusEffectsSnapshot {
        if (resistanceAmplifier < -1) {
            throw new IllegalArgumentException("resistanceAmplifier must be -1 or greater");
        }
        effects = Map.copyOf(Objects.requireNonNull(effects, "effects"));
    }

    public StatusEffectsSnapshot(boolean fireResistance, int resistanceAmplifier) {
        this(fireResistance, resistanceAmplifier, Map.of());
    }

    public static StatusEffectsSnapshot none() {
        return new StatusEffectsSnapshot(false, -1, Map.of());
    }

    public Optional<EffectInstanceSnapshot> effect(String key) {
        return Optional.ofNullable(effects.get(key));
    }

    public StatusEffectsSnapshot clearAll() {
        return none();
    }

    public StatusEffectsSnapshot apply(List<EffectInstanceSnapshot> additions) {
        LinkedHashMap<String, EffectInstanceSnapshot> next = new LinkedHashMap<>(effects);
        boolean nextFireResistance = fireResistance;
        int nextResistanceAmplifier = resistanceAmplifier;
        for (EffectInstanceSnapshot effect : additions) {
            next.put(effect.effectKey(), effect);
            if (effect.effectKey().equals("minecraft:fire_resistance")) nextFireResistance = true;
            if (effect.effectKey().equals("minecraft:resistance")) nextResistanceAmplifier = effect.amplifier();
        }
        return new StatusEffectsSnapshot(nextFireResistance, nextResistanceAmplifier, next);
    }
}
