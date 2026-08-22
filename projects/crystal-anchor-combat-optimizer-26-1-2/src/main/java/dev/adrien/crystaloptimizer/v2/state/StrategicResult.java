package dev.adrien.crystaloptimizer.v2.state;

import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import java.util.Objects;
import java.util.UUID;

/** Revision-stamped worker output. Later tasks may extend this with a planned sequence. */
public record StrategicResult(
    long snapshotId,
    long worldRevision,
    long inventoryRevision,
    long configRevision,
    UUID targetId,
    DamageMap damageMap
) {
    public StrategicResult {
        if (snapshotId < 0L || worldRevision < 0L || inventoryRevision < 0L || configRevision < 0L) {
            throw new IllegalArgumentException("strategic revisions must be non-negative");
        }
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(damageMap, "damageMap");
        if (!targetId.equals(damageMap.targetId())) {
            throw new IllegalArgumentException("targetId must match damage map");
        }
    }
}
