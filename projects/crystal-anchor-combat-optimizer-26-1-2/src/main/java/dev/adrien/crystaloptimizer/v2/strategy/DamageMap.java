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
    Map<String, DamageOpportunity> opportunities,
    Map<String, Integer> candidateCounts
) {
    public DamageMap(
        UUID targetId,
        long targetRevision,
        long worldRevision,
        Map<String, DamageOpportunity> opportunities
    ) {
        this(targetId, targetRevision, worldRevision, opportunities, Map.of());
    }

    public DamageMap {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(opportunities, "opportunities");
        Objects.requireNonNull(candidateCounts, "candidateCounts");
        opportunities = Collections.unmodifiableMap(new LinkedHashMap<>(opportunities));
        LinkedHashMap<String, Integer> normalizedCounts = new LinkedHashMap<>();
        candidateCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                Objects.requireNonNull(entry.getKey(), "candidate category");
                Objects.requireNonNull(entry.getValue(), "candidate count");
                if (entry.getValue() < 0) {
                    throw new IllegalArgumentException("candidate count must be non-negative");
                }
                normalizedCounts.put(entry.getKey(), entry.getValue());
            });
        candidateCounts = Collections.unmodifiableMap(normalizedCounts);
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
        return new DamageMap(targetId, targetRevision, worldRevision + 1L, kept, candidateCounts);
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
        return new DamageMap(targetId, nextTargetRevision, worldRevision, kept, candidateCounts);
    }

    public static DamageMap empty(UUID targetId, long targetRevision, long worldRevision) {
        return new DamageMap(targetId, targetRevision, worldRevision, Map.of(), Map.of());
    }
}
