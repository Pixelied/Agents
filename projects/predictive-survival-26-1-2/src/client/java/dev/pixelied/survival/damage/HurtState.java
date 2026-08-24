package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;

import java.util.Objects;

public record HurtState(DamageRange lastHurt, int invulnerableTime, Confidence confidence) {
    public HurtState {
        lastHurt = Objects.requireNonNull(lastHurt, "lastHurt");
        confidence = Objects.requireNonNull(confidence, "confidence");
        if (invulnerableTime < 0) {
            throw new IllegalArgumentException("invulnerableTime must be non-negative");
        }
    }

    public static HurtState unknown() {
        return new HurtState(DamageRange.exact(0f), 0, Confidence.UNKNOWN);
    }
}
