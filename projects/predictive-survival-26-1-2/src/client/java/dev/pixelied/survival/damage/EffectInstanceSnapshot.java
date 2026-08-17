package dev.pixelied.survival.damage;

import java.util.Objects;

public record EffectInstanceSnapshot(String effectKey, int durationTicks, int amplifier) {
    public EffectInstanceSnapshot {
        effectKey = Objects.requireNonNull(effectKey, "effectKey");
        if (effectKey.isBlank()) throw new IllegalArgumentException("effectKey must not be blank");
        if (durationTicks < 0) throw new IllegalArgumentException("durationTicks must be non-negative");
        if (amplifier < 0) throw new IllegalArgumentException("amplifier must be non-negative");
    }
}
