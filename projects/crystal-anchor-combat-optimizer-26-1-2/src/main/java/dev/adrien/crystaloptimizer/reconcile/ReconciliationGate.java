package dev.adrien.crystaloptimizer.reconcile;

import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public final class ReconciliationGate {
    private final CombatEvidence baseline;
    private final long startedNanos;
    private final long timeoutNanos;

    private ReconciliationGate(CombatEvidence baseline, long startedNanos, long timeoutNanos) {
        this.baseline = Objects.requireNonNull(baseline, "baseline");
        if (startedNanos < 0L) {
            throw new IllegalArgumentException("startedNanos must be non-negative");
        }
        if (timeoutNanos <= 0L) {
            throw new IllegalArgumentException("timeoutNanos must be positive");
        }
        this.startedNanos = startedNanos;
        this.timeoutNanos = timeoutNanos;
    }

    public static ReconciliationGate start(
        CombatSnapshot snapshot,
        UUID targetId,
        long startedNanos,
        long timeoutNanos
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(targetId, "targetId");
        return new ReconciliationGate(
            CombatEvidence.from(snapshot, targetId),
            startedNanos,
            timeoutNanos
        );
    }

    public Status evaluate(CombatSnapshot snapshot, UUID targetId, long nowNanos) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(targetId, "targetId");
        if (nowNanos < startedNanos) {
            throw new IllegalArgumentException("nowNanos cannot precede gate start");
        }

        if (!CombatEvidence.from(snapshot, targetId).equals(baseline)) {
            return Status.CONFIRMED;
        }
        return nowNanos - startedNanos >= timeoutNanos
            ? Status.TIMED_OUT
            : Status.WAITING;
    }

    public enum Status {
        WAITING,
        CONFIRMED,
        TIMED_OUT
    }

    private record CombatEvidence(
        float targetHealth,
        float targetAbsorption,
        boolean targetDead,
        Object targetTotem,
        List<CrystalEvidence> crystals,
        Map<BlockPos, AnchorState> anchors
    ) {
        private static CombatEvidence from(CombatSnapshot snapshot, UUID targetId) {
            SimCombatant target = snapshot.combatants().get(targetId);
            if (target == null) {
                throw new IllegalArgumentException("target is absent from snapshot: " + targetId);
            }
            List<CrystalEvidence> crystals = snapshot.crystals().stream()
                .map(CrystalEvidence::from)
                .sorted(java.util.Comparator.comparingInt(CrystalEvidence::entityId))
                .toList();
            return new CombatEvidence(
                target.health(),
                target.absorption(),
                target.dead(),
                target.totem(),
                crystals,
                snapshot.anchors()
            );
        }
    }

    private record CrystalEvidence(int entityId, Vec3 position) {
        private static CrystalEvidence from(KnownCrystal crystal) {
            return new CrystalEvidence(crystal.entityId(), crystal.position());
        }
    }
}
