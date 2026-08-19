package dev.adrien.crystaloptimizer.v2.state;

import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.Objects;
import java.util.UUID;

public record ActionApproval(
    long approvalId,
    UUID targetId,
    ApprovalSlot slot,
    ReactiveActionSpec actionSpec,
    DamageEstimate targetDamage,
    float worstCaseSelfDamage,
    SequenceTiming timing,
    long worldRevision,
    long targetRevision,
    long inventoryRevision,
    long configRevision,
    long expiresAtNanos
) {
    public ActionApproval {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(actionSpec, "actionSpec");
        Objects.requireNonNull(targetDamage, "targetDamage");
        Objects.requireNonNull(timing, "timing");
        if (!Float.isFinite(worstCaseSelfDamage) || worstCaseSelfDamage < 0.0f) {
            throw new IllegalArgumentException("worstCaseSelfDamage must be finite and non-negative");
        }
    }

    public boolean isCurrent(
        long currentWorldRevision,
        long currentTargetRevision,
        long currentInventoryRevision,
        long currentConfigRevision,
        long nowNanos
    ) {
        return worldRevision == currentWorldRevision
            && targetRevision == currentTargetRevision
            && inventoryRevision == currentInventoryRevision
            && configRevision == currentConfigRevision
            && nowNanos <= expiresAtNanos;
    }
}
