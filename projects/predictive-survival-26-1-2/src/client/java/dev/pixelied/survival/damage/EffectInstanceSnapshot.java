package dev.pixelied.survival.damage;

import java.util.Objects;
import java.util.Optional;

public record EffectInstanceSnapshot(
    String effectKey,
    int durationTicks,
    int amplifier,
    Optional<EffectInstanceSnapshot> hiddenEffect
) {
    public EffectInstanceSnapshot {
        effectKey = Objects.requireNonNull(effectKey, "effectKey");
        hiddenEffect = Objects.requireNonNull(hiddenEffect, "hiddenEffect");
        if (effectKey.isBlank()) throw new IllegalArgumentException("effectKey must not be blank");
        if (durationTicks < -1) throw new IllegalArgumentException("durationTicks must be -1 or non-negative");
        if (amplifier < 0) throw new IllegalArgumentException("amplifier must be non-negative");
        if (hiddenEffect.isPresent() && !effectKey.equals(hiddenEffect.get().effectKey())) {
            throw new IllegalArgumentException("hidden effect must have the same effect key");
        }
    }

    public EffectInstanceSnapshot(String effectKey, int durationTicks, int amplifier) {
        this(effectKey, durationTicks, amplifier, Optional.empty());
    }

    public boolean infiniteDuration() {
        return durationTicks == -1;
    }
}
