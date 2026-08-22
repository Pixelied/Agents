package dev.adrien.crystaloptimizer.execution;

import java.util.OptionalInt;

/** Minimal immutable attack-relevant effect snapshot for interaction routing. */
public record StatusEffectSnapshot(
    OptionalInt strengthAmplifier,
    OptionalInt weaknessAmplifier
) {
    public StatusEffectSnapshot {
        if (strengthAmplifier == null || weaknessAmplifier == null) {
            throw new NullPointerException("effect amplifier");
        }
        validateAmplifier(strengthAmplifier);
        validateAmplifier(weaknessAmplifier);
    }

    public static StatusEffectSnapshot none() {
        return new StatusEffectSnapshot(OptionalInt.empty(), OptionalInt.empty());
    }

    public static StatusEffectSnapshot weakness(int amplifier) {
        return new StatusEffectSnapshot(OptionalInt.empty(), OptionalInt.of(amplifier));
    }

    public static StatusEffectSnapshot strength(int amplifier) {
        return new StatusEffectSnapshot(OptionalInt.of(amplifier), OptionalInt.empty());
    }

    public double attackDamageDelta() {
        double strength = strengthAmplifier.isPresent()
            ? 3.0 * (strengthAmplifier.getAsInt() + 1)
            : 0.0;
        double weakness = weaknessAmplifier.isPresent()
            ? 4.0 * (weaknessAmplifier.getAsInt() + 1)
            : 0.0;
        return strength - weakness;
    }

    private static void validateAmplifier(OptionalInt amplifier) {
        if (amplifier.isPresent() && amplifier.getAsInt() < 0) {
            throw new IllegalArgumentException("effect amplifier must be non-negative");
        }
    }
}
