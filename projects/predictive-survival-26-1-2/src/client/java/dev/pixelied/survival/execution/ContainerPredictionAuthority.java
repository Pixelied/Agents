package dev.pixelied.survival.execution;

import dev.pixelied.survival.inventory.InventorySlotSnapshot;

import java.util.Objects;

/**
 * Reconciles Minecraft 26.1.2's optimistic container-click protocol without pretending that a
 * successful click has an ACK. The client predicts the click locally; on the server a matching
 * state-id click seeds the remote-slot cache from the client prediction before broadcastChanges().
 * Therefore an exact accepted prediction is normally silent, while disagreement produces slot or
 * full-content correction packets. A silent prediction is accepted only after the conservative
 * correction-return deadline captured when the click was sent.
 */
public final class ContainerPredictionAuthority {
    private final int containerId;
    private final int sentStateId;
    private final int sourceInventoryIndex;
    private final InventorySlotSnapshot expectedSourceAfter;
    private final int destinationInventoryIndex;
    private final InventorySlotSnapshot expectedDestinationAfter;
    private final long authorityRevisionBeforeClick;
    private final long settleAtServerTick;

    public ContainerPredictionAuthority(
        int containerId,
        int sentStateId,
        int sourceInventoryIndex,
        InventorySlotSnapshot expectedSourceAfter,
        int destinationInventoryIndex,
        InventorySlotSnapshot expectedDestinationAfter,
        long authorityRevisionBeforeClick,
        long settleAtServerTick
    ) {
        if (containerId < 0 || sentStateId < 0) {
            throw new IllegalArgumentException("container/state id must be non-negative");
        }
        if (sourceInventoryIndex < 0 || sourceInventoryIndex > 40
            || destinationInventoryIndex < 0 || destinationInventoryIndex > 40) {
            throw new IllegalArgumentException("inventory indices must be in [0, 40]");
        }
        if (authorityRevisionBeforeClick < 0L || settleAtServerTick < 0L) {
            throw new IllegalArgumentException("authority revision/settle tick must be non-negative");
        }
        this.containerId = containerId;
        this.sentStateId = sentStateId;
        this.sourceInventoryIndex = sourceInventoryIndex;
        this.expectedSourceAfter = Objects.requireNonNull(expectedSourceAfter, "expectedSourceAfter");
        this.destinationInventoryIndex = destinationInventoryIndex;
        this.expectedDestinationAfter = Objects.requireNonNull(expectedDestinationAfter, "expectedDestinationAfter");
        this.authorityRevisionBeforeClick = authorityRevisionBeforeClick;
        this.settleAtServerTick = settleAtServerTick;
    }

    public Verdict evaluate(ExecutionContext context) {
        Objects.requireNonNull(context, "context");
        if (context.menu().containerId() != containerId) return Verdict.CONTRADICTED;

        InventorySlotSnapshot source = context.inventory().slot(sourceInventoryIndex).orElse(null);
        InventorySlotSnapshot destination = context.inventory().slot(destinationInventoryIndex).orElse(null);
        if (source == null || destination == null) return Verdict.CONTRADICTED;

        boolean exactPostState = source.sameContents(expectedSourceAfter)
            && destination.sameContents(expectedDestinationAfter);
        boolean menuRevisionObserved = context.menu().stateId() != sentStateId;

        ServerStateEvidenceSnapshot evidence = context.serverStateEvidence();
        boolean sourceObservedAfter = observedAfter(evidence, sourceInventoryIndex);
        boolean destinationObservedAfter = observedAfter(evidence, destinationInventoryIndex);
        boolean exactInboundEvidence = evidence.known()
            && evidence.inventoryMatchesAfter(
                sourceInventoryIndex, expectedSourceAfter, authorityRevisionBeforeClick
            )
            && evidence.inventoryMatchesAfter(
                destinationInventoryIndex, expectedDestinationAfter, authorityRevisionBeforeClick
            );
        boolean routedSlotCorrectionObserved = sourceObservedAfter || destinationObservedAfter;

        if (exactPostState) {
            if (exactInboundEvidence || menuRevisionObserved) return Verdict.ACCEPTED;
            if (routedSlotCorrectionObserved) return Verdict.CONTRADICTED;
            return context.currentServerTick() >= settleAtServerTick
                ? Verdict.ACCEPTED
                : Verdict.WAITING;
        }

        if (menuRevisionObserved || routedSlotCorrectionObserved || context.currentServerTick() >= settleAtServerTick) {
            return Verdict.CONTRADICTED;
        }
        return Verdict.WAITING;
    }

    public long settleAtServerTick() {
        return settleAtServerTick;
    }

    private boolean observedAfter(ServerStateEvidenceSnapshot evidence, int inventoryIndex) {
        if (!evidence.known()) return false;
        ServerStateEvidenceSnapshot.StackEvidence slot = evidence.inventorySlots().get(inventoryIndex);
        return slot != null && slot.revision() > authorityRevisionBeforeClick;
    }

    public enum Verdict {
        WAITING,
        ACCEPTED,
        CONTRADICTED
    }
}
