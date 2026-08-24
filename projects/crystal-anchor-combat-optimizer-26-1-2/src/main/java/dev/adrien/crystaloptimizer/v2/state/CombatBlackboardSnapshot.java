package dev.adrien.crystaloptimizer.v2.state;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record CombatBlackboardSnapshot(
    UUID targetId,
    long targetRevision,
    long worldRevision,
    long inventoryRevision,
    long configRevision,
    Map<ApprovalSlot, ActionApproval> approvals
) {
    public CombatBlackboardSnapshot {
        Objects.requireNonNull(approvals, "approvals");
        approvals = Map.copyOf(approvals);
    }

    public static CombatBlackboardSnapshot empty() {
        return new CombatBlackboardSnapshot(null, 0L, 0L, 0L, 0L, Map.of());
    }
}
