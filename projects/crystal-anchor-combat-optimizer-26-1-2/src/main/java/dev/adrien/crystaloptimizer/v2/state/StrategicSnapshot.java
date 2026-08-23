package dev.adrien.crystaloptimizer.v2.state;

import dev.adrien.crystaloptimizer.prediction.MovementSample;
import dev.adrien.crystaloptimizer.v2.strategy.TargetProtectionPolicyConfig;
import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Complete immutable input for one strategic worker computation. */
public record StrategicSnapshot(
    long snapshotId,
    long worldRevision,
    long inventoryRevision,
    long configRevision,
    long capturedAtNanos,
    UUID selfId,
    Map<UUID, Long> targetRevisions,
    CombatSnapshot combat,
    Map<UUID, List<MovementSample>> movementHistory,
    Set<UUID> protectedPlayerIds,
    TargetProtectionPolicyConfig targetProtection,
    TimingSnapshot timing
) {
    public StrategicSnapshot {
        if (snapshotId < 0L || worldRevision < 0L || inventoryRevision < 0L
            || configRevision < 0L || capturedAtNanos < 0L) {
            throw new IllegalArgumentException("strategic revisions and timestamps must be non-negative");
        }
        Objects.requireNonNull(selfId, "selfId");
        Objects.requireNonNull(targetRevisions, "targetRevisions");
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(movementHistory, "movementHistory");
        Objects.requireNonNull(protectedPlayerIds, "protectedPlayerIds");
        Objects.requireNonNull(targetProtection, "targetProtection");
        Objects.requireNonNull(timing, "timing");
        if (!selfId.equals(combat.selfId())) {
            throw new IllegalArgumentException("selfId must match combat snapshot");
        }

        targetRevisions = Map.copyOf(targetRevisions);
        LinkedHashMap<UUID, List<MovementSample>> historyCopy = new LinkedHashMap<>();
        movementHistory.forEach((id, samples) -> historyCopy.put(
            Objects.requireNonNull(id, "movement history id"),
            List.copyOf(Objects.requireNonNull(samples, "movement samples"))
        ));
        movementHistory = Map.copyOf(historyCopy);
        protectedPlayerIds = Set.copyOf(protectedPlayerIds);
    }
}
