package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class TargetEligibilityPolicy {
    public static boolean isEligible(
        CombatSnapshot snapshot,
        UUID candidateId,
        Set<UUID> protectedIds
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(candidateId, "candidateId");
        Objects.requireNonNull(protectedIds, "protectedIds");
        if (candidateId.equals(snapshot.selfId()) || protectedIds.contains(candidateId)) {
            return false;
        }
        SimCombatant candidate = snapshot.combatants().get(candidateId);
        return candidate != null
            && !candidate.dead()
            && candidate.health() > 0.0f
            && snapshot.spatial().containsKey(candidateId);
    }

    private TargetEligibilityPolicy() {
    }
}
