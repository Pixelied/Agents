package dev.adrien.crystaloptimizer.v2.strategy;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public record DamageMap(
    UUID targetId,
    long targetRevision,
    long worldRevision,
    Map<String, DamageOpportunity> opportunities
) {
    public DamageMap {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(opportunities, "opportunities");
        opportunities = Collections.unmodifiableMap(new LinkedHashMap<>(opportunities));
    }

    public DamageMap invalidateGeometry(Set<BlockPos> changedPositions) {
        Objects.requireNonNull(changedPositions, "changedPositions");
        if (changedPositions.isEmpty()) {
            return this;
        }
        LinkedHashMap<String, DamageOpportunity> kept = new LinkedHashMap<>();
        for (var entry : opportunities.entrySet()) {
            boolean affected = entry.getValue().geometryDependencies().stream()
                .anyMatch(changedPositions::contains);
            if (!affected) {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        return new DamageMap(targetId, targetRevision, worldRevision + 1L, kept);
    }

    public DamageMap withTargetRevision(long nextTargetRevision) {
        if (nextTargetRevision == targetRevision) {
            return this;
        }
        LinkedHashMap<String, DamageOpportunity> kept = new LinkedHashMap<>();
        for (var entry : opportunities.entrySet()) {
            if (!entry.getValue().positionDependent()) {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        return new DamageMap(targetId, nextTargetRevision, worldRevision, kept);
    }

    public static DamageMap empty(UUID targetId, long targetRevision, long worldRevision) {
        return new DamageMap(targetId, targetRevision, worldRevision, Map.of());
    }
}
