package dev.adrien.crystaloptimizer.v2.state;

import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import dev.adrien.crystaloptimizer.v2.strategy.PlannedOpportunity;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Revision-stamped worker output, including an optional precomputed strategic sequence. */
public record StrategicResult(
    long snapshotId,
    long worldRevision,
    long inventoryRevision,
    long configRevision,
    UUID targetId,
    DamageMap damageMap,
    Optional<PlannedOpportunity> plannedOpportunity
) {
    public StrategicResult(
        long snapshotId,
        long worldRevision,
        long inventoryRevision,
        long configRevision,
        UUID targetId,
        DamageMap damageMap
    ) {
        this(
            snapshotId,
            worldRevision,
            inventoryRevision,
            configRevision,
            targetId,
            damageMap,
            Optional.empty()
        );
    }

    public StrategicResult {
        if (snapshotId < 0L || worldRevision < 0L || inventoryRevision < 0L || configRevision < 0L) {
            throw new IllegalArgumentException("strategic revisions must be non-negative");
        }
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(damageMap, "damageMap");
        Objects.requireNonNull(plannedOpportunity, "plannedOpportunity");
        if (!targetId.equals(damageMap.targetId())) {
            throw new IllegalArgumentException("targetId must match damage map");
        }
    }
}
