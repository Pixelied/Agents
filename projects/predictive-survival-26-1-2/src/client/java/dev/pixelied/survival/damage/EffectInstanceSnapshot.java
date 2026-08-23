package dev.pixelied.survival.damage;

import java.util.Objects;

public record EffectInstanceSnapshot(
    String effectKey,
    int durationTicks,
    int amplifier,
    boolean hiddenTailUnknown
) {
    public EffectInstanceSnapshot {
        effectKey = Objects.requireNonNull(effectKey, "effectKey");
        if (effectKey.isBlank()) throw new IllegalArgumentException("effectKey must not be blank");
        if (durationTicks < -1) throw new IllegalArgumentException("durationTicks must be -1 or non-negative");
        if (amplifier < 0) throw new IllegalArgumentException("amplifier must be non-negative");
    }

    /**
     * Deterministic/synthetic effect snapshots know that no unrepresented hidden tail exists.
     * Live Minecraft packet captures use the four-argument constructor because 26.1.2 omits
     * MobEffectInstance.hiddenEffect from ClientboundUpdateMobEffectPacket.
     */
    public EffectInstanceSnapshot(String effectKey, int durationTicks, int amplifier) {
        this(effectKey, durationTicks, amplifier, false);
    }

    public boolean infiniteDuration() {
        return durationTicks == -1;
    }
}
