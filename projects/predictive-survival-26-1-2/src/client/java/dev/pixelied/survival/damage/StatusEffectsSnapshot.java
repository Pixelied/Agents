package dev.pixelied.survival.damage;

public record StatusEffectsSnapshot(boolean fireResistance, int resistanceAmplifier) {
    public StatusEffectsSnapshot {
        if (resistanceAmplifier < -1) {
            throw new IllegalArgumentException("resistanceAmplifier must be -1 or greater");
        }
    }

    public static StatusEffectsSnapshot none() {
        return new StatusEffectsSnapshot(false, -1);
    }
}
