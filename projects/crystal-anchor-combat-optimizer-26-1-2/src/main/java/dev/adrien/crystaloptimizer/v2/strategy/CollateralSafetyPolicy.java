package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class CollateralSafetyPolicy {
    private static final double CERTIFIED_CONFIDENCE = 0.80;

    public static boolean accepts(
        Map<UUID, DamageEstimate> protectedDamage,
        CombatSnapshot snapshot,
        float maxProtectedDamage
    ) {
        Objects.requireNonNull(protectedDamage, "protectedDamage");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!Float.isFinite(maxProtectedDamage) || maxProtectedDamage < 0.0f) {
            throw new IllegalArgumentException("maxProtectedDamage must be finite and non-negative");
        }
        for (var entry : protectedDamage.entrySet()) {
            UUID protectedId = Objects.requireNonNull(entry.getKey(), "protected player id");
            DamageEstimate estimate = Objects.requireNonNull(entry.getValue(), "protected damage estimate");
            if (protectedId.equals(snapshot.selfId())) {
                continue;
            }
            boolean certifiedFatal = estimate.killProbability() == 1.0
                && estimate.confidence() >= CERTIFIED_CONFIDENCE;
            if (certifiedFatal || estimate.upperBound() > maxProtectedDamage) {
                return false;
            }
        }
        return true;
    }

    private CollateralSafetyPolicy() {
    }
}
