package dev.adrien.crystaloptimizer.v2.strategy;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public record TargetProtectionPolicyConfig(
    Set<UUID> protectedPlayerIds,
    boolean protectScoreboardTeam,
    float maxProtectedDamage
) {
    public TargetProtectionPolicyConfig {
        Objects.requireNonNull(protectedPlayerIds, "protectedPlayerIds");
        protectedPlayerIds = Set.copyOf(protectedPlayerIds);
        if (!Float.isFinite(maxProtectedDamage) || maxProtectedDamage < 0.0f) {
            throw new IllegalArgumentException("maxProtectedDamage must be finite and non-negative");
        }
    }

    public static TargetProtectionPolicyConfig defaults() {
        return new TargetProtectionPolicyConfig(Set.of(), true, 0.5f);
    }
}
