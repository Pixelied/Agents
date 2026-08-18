package dev.adrien.crystaloptimizer.sim.model;

import java.util.Optional;

public record EffectState(
    Optional<EffectInstance> resistance,
    Optional<EffectInstance> regeneration,
    Optional<EffectInstance> absorption,
    Optional<EffectInstance> fireResistance
) {
    public EffectState {
        resistance = resistance == null ? Optional.empty() : resistance;
        regeneration = regeneration == null ? Optional.empty() : regeneration;
        absorption = absorption == null ? Optional.empty() : absorption;
        fireResistance = fireResistance == null ? Optional.empty() : fireResistance;
    }

    public static EffectState empty() {
        return new EffectState(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public static EffectState resistance(int amplifier, int durationTicks) {
        return new EffectState(
            Optional.of(new EffectInstance(amplifier, durationTicks)),
            Optional.empty(),
            Optional.empty(),
            Optional.empty()
        );
    }

    public static EffectState totemEffects() {
        return new EffectState(
            Optional.empty(),
            Optional.of(new EffectInstance(1, 900)),
            Optional.of(new EffectInstance(1, 100)),
            Optional.of(new EffectInstance(0, 800))
        );
    }

    public boolean hasResistance() {
        return resistance.isPresent();
    }

    public int resistanceAmplifier() {
        return resistance.map(EffectInstance::amplifier).orElse(-1);
    }

    public EffectState clearAll() {
        return empty();
    }

    public record EffectInstance(int amplifier, int durationTicks) {
    }
}
