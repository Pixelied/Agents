package dev.adrien.crystaloptimizer.v2.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;
import java.util.List;
import java.util.Objects;

public record ArbitrationResult(
    boolean allowed,
    Reason reason,
    List<CombatAction> actions
) {
    public ArbitrationResult {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(actions, "actions");
        actions = List.copyOf(actions);
        if (allowed && reason != Reason.NONE) {
            throw new IllegalArgumentException("allowed result must use NONE reason");
        }
        if (!allowed && reason == Reason.NONE) {
            throw new IllegalArgumentException("rejected result needs a reason");
        }
    }

    public static ArbitrationResult approved(List<CombatAction> actions) {
        return new ArbitrationResult(true, Reason.NONE, actions);
    }

    public static ArbitrationResult rejected(Reason reason) {
        return new ArbitrationResult(false, reason, List.of());
    }

    public enum Reason {
        NONE,
        STALE_APPROVAL,
        INVALID_TARGET,
        SELF_DAMAGE_LIMIT,
        FEATURE_DISABLED,
        ENTITY_GONE,
        ENTITY_OUT_OF_REACH,
        BLOCK_OUT_OF_REACH,
        ILLEGAL_TRANSITION,
        ITEM_UNAVAILABLE,
        UNSUPPORTED_ACTION
    }
}
