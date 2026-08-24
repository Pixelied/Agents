package dev.adrien.crystaloptimizer.v2.state;

import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.strategy.OpportunityIntent;
import dev.adrien.crystaloptimizer.v2.strategy.ResourceChain;
import dev.adrien.crystaloptimizer.v2.strategy.SelfDamageEstimate;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.Objects;
import java.util.UUID;

public record ActionApproval(
    long approvalId,
    UUID targetId,
    ApprovalSlot slot,
    ReactiveActionSpec actionSpec,
    DamageEstimate targetDamage,
    OpportunityIntent intent,
    SelfDamageEstimate selfDamage,
    ResourceChain resources,
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
        Objects.requireNonNull(intent, "intent");
        Objects.requireNonNull(selfDamage, "selfDamage");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(timing, "timing");
    }

    public ActionApproval(
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
        this(
            approvalId,
            targetId,
            slot,
            actionSpec,
            targetDamage,
            OpportunityIntent.PRESSURE,
            SelfDamageEstimate.legacy(worstCaseSelfDamage),
            ResourceChain.none(),
            timing,
            worldRevision,
            targetRevision,
            inventoryRevision,
            configRevision,
            expiresAtNanos
        );
    }

    public float worstCaseSelfDamage() {
        return selfDamage.worstCaseDamage();
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
