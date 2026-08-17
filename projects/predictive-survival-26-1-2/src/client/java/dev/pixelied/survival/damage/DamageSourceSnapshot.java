package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.Vec3Snapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record DamageSourceSnapshot(
    DamageRange rawDamage,
    Set<DamageFlag> flags,
    boolean scalesWithDifficulty,
    float freezingMultiplier,
    boolean piercingProjectile,
    Optional<Vec3Snapshot> sourcePosition,
    String sourceKey,
    float applicationHealthThresholdExclusive
) {
    public DamageSourceSnapshot(
        DamageRange rawDamage,
        Set<DamageFlag> flags,
        boolean scalesWithDifficulty,
        float freezingMultiplier,
        boolean piercingProjectile,
        Optional<Vec3Snapshot> sourcePosition,
        String sourceKey
    ) {
        this(rawDamage, flags, scalesWithDifficulty, freezingMultiplier, piercingProjectile,
            sourcePosition, sourceKey, 0f);
    }

    public DamageSourceSnapshot {
        rawDamage = Objects.requireNonNull(rawDamage, "rawDamage");
        flags = Set.copyOf(Objects.requireNonNull(flags, "flags"));
        sourcePosition = Objects.requireNonNull(sourcePosition, "sourcePosition");
        sourceKey = Objects.requireNonNull(sourceKey, "sourceKey");
        if (freezingMultiplier < 0f || Float.isNaN(freezingMultiplier)) {
            throw new IllegalArgumentException("freezingMultiplier must be non-negative");
        }
        if (!Float.isFinite(applicationHealthThresholdExclusive) || applicationHealthThresholdExclusive < 0f) {
            throw new IllegalArgumentException("applicationHealthThresholdExclusive must be finite and non-negative");
        }
    }

    public boolean has(DamageFlag flag) {
        return flags.contains(flag);
    }
}
