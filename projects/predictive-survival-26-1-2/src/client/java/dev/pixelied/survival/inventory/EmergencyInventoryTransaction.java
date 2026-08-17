package dev.pixelied.survival.inventory;

import java.util.Objects;

public final class EmergencyInventoryTransaction {
    public enum State {
        PLANNED,
        SENT,
        AWAITING_RECONCILE,
        CONFIRMED,
        CONTRADICTED,
        CONSUMED,
        RESTORING,
        DONE
    }

    private final DeathProtectionRoute.ContainerSwap route;
    private final int containerId;
    private final int stateId;
    private final InventorySlotSnapshot sourceBefore;
    private final InventorySlotSnapshot destinationBefore;
    private final long sendTick;
    private final long deadlineTick;
    private final State state;
    private final boolean originalDestinationRestorable;

    private EmergencyInventoryTransaction(
        DeathProtectionRoute.ContainerSwap route,
        int containerId,
        int stateId,
        InventorySlotSnapshot sourceBefore,
        InventorySlotSnapshot destinationBefore,
        long sendTick,
        long deadlineTick,
        State state,
        boolean originalDestinationRestorable
    ) {
        this.route = Objects.requireNonNull(route, "route");
        if (containerId < 0 || stateId < 0) throw new IllegalArgumentException("containerId/stateId must be non-negative");
        this.containerId = containerId;
        this.stateId = stateId;
        this.sourceBefore = Objects.requireNonNull(sourceBefore, "sourceBefore");
        this.destinationBefore = Objects.requireNonNull(destinationBefore, "destinationBefore");
        if (sendTick < 0 || deadlineTick < sendTick) throw new IllegalArgumentException("invalid send/deadline ticks");
        this.sendTick = sendTick;
        this.deadlineTick = deadlineTick;
        this.state = Objects.requireNonNull(state, "state");
        this.originalDestinationRestorable = originalDestinationRestorable;
    }

    public static EmergencyInventoryTransaction planned(
        DeathProtectionRoute.ContainerSwap route,
        int containerId,
        int stateId,
        InventorySlotSnapshot sourceBefore,
        InventorySlotSnapshot destinationBefore,
        long sendTick,
        long deadlineTick
    ) {
        return new EmergencyInventoryTransaction(
            route, containerId, stateId, sourceBefore, destinationBefore,
            sendTick, deadlineTick, State.PLANNED, true
        );
    }

    public State state() {
        return state;
    }

    public DeathProtectionRoute.ContainerSwap route() {
        return route;
    }

    public int containerId() {
        return containerId;
    }

    public int stateId() {
        return stateId;
    }

    public long sendTick() {
        return sendTick;
    }

    public long deadlineTick() {
        return deadlineTick;
    }

    public boolean canRestoreOriginalDestinationStack() {
        return originalDestinationRestorable
            && state != State.CONSUMED
            && state != State.CONTRADICTED
            && state != State.DONE;
    }

    public EmergencyInventoryTransaction markSent() {
        requireState(State.PLANNED);
        return copy(State.SENT, originalDestinationRestorable);
    }

    public EmergencyInventoryTransaction observeStateIdMismatch() {
        requireState(State.SENT);
        return copy(State.AWAITING_RECONCILE, originalDestinationRestorable);
    }

    public EmergencyInventoryTransaction reconcile(
        InventorySlotSnapshot authoritativeSource,
        InventorySlotSnapshot authoritativeDestination
    ) {
        if (state != State.SENT && state != State.AWAITING_RECONCILE) {
            throw new IllegalStateException("reconcile requires SENT or AWAITING_RECONCILE");
        }
        boolean expectedSwap = authoritativeSource.sameContents(destinationBefore)
            && authoritativeDestination.sameContents(sourceBefore);
        return copy(expectedSwap ? State.CONFIRMED : State.CONTRADICTED, expectedSwap && originalDestinationRestorable);
    }

    public EmergencyInventoryTransaction contradict() {
        return copy(State.CONTRADICTED, false);
    }

    public EmergencyInventoryTransaction attemptRestore(boolean lethalThreatStillPending) {
        if (lethalThreatStillPending) return this;
        if (state != State.CONFIRMED || !canRestoreOriginalDestinationStack()) return this;
        return copy(State.RESTORING, true);
    }

    public EmergencyInventoryTransaction markConsumed() {
        if (state != State.CONFIRMED && state != State.AWAITING_RECONCILE && state != State.SENT) {
            throw new IllegalStateException("consumption requires an active emergency transaction");
        }
        return copy(State.CONSUMED, false);
    }

    public EmergencyInventoryTransaction markRestored() {
        requireState(State.RESTORING);
        return copy(State.DONE, false);
    }

    private EmergencyInventoryTransaction copy(State nextState, boolean restorable) {
        return new EmergencyInventoryTransaction(
            route, containerId, stateId, sourceBefore, destinationBefore,
            sendTick, deadlineTick, nextState, restorable
        );
    }

    private void requireState(State expected) {
        if (state != expected) {
            throw new IllegalStateException("expected state " + expected + " but was " + state);
        }
    }
}
