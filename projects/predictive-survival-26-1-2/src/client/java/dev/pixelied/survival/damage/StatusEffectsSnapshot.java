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

    /**
     * Advances finite effect durations by the supplied number of game ticks.
     *
     * <p>The two legacy summary fields are kept for snapshots that do not contain per-effect
     * duration data. When a concrete effect entry is present, however, that entry is authoritative
     * and its expiry must also clear the corresponding summary field.</p>
     */
    public StatusEffectsSnapshot age(int elapsedTicks) {
        if (elapsedTicks < 0) throw new IllegalArgumentException("elapsedTicks must be non-negative");
        if (elapsedTicks == 0 || effects.isEmpty()) return this;

        LinkedHashMap<String, EffectInstanceSnapshot> next = new LinkedHashMap<>();
        for (EffectInstanceSnapshot effect : effects.values()) {
            if (effect.infiniteDuration()) {
                next.put(effect.effectKey(), effect);
                continue;
            }

            int remaining = Math.max(0, effect.durationTicks() - elapsedTicks);
            if (remaining > 0) {
                next.put(effect.effectKey(), new EffectInstanceSnapshot(
                    effect.effectKey(), remaining, effect.amplifier(), effect.hiddenTailUnknown()
                ));
            }
        }

        boolean nextFireResistance = fireResistance;
        if (effects.containsKey("minecraft:fire_resistance")) {
            nextFireResistance = next.containsKey("minecraft:fire_resistance");
        }

        int nextResistanceAmplifier = resistanceAmplifier;
        if (effects.containsKey("minecraft:resistance")) {
            EffectInstanceSnapshot resistance = next.get("minecraft:resistance");
            nextResistanceAmplifier = resistance == null ? -1 : resistance.amplifier();
        }

        return new StatusEffectsSnapshot(nextFireResistance, nextResistanceAmplifier, next);
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
