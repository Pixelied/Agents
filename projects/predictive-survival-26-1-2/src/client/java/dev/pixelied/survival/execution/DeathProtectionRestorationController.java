package dev.pixelied.survival.execution;

import dev.pixelied.survival.inventory.EmergencyInventoryTransaction;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Reverses only server-confirmed emergency hand mutations after danger has remained absent through
 * a timing-derived protection hold lease. Mutations are restored in reverse order and any
 * disagreement aborts the entire chain rather than overwriting player state.
 */
public final class DeathProtectionRestorationController {
    private static final int MAX_RESTORATION_DEPTH = 8;

    private final Deque<RestorationCheckpoint> checkpoints = new ArrayDeque<>();
    private final ProtectionHoldLease holdLease = new ProtectionHoldLease();
    private PendingRestore pendingRestore;
    private boolean leaseNeedsRequirement;
    private long armedUserIntentGeneration = ManualUserIntentTracker.global().generation();
    private boolean userIntentInvalidatedRestoreChain;

    public void arm(RestorationCheckpoint checkpoint) {
        RestorationCheckpoint next = Objects.requireNonNull(checkpoint, "checkpoint");

        // A new emergency mutation while an inverse mutation is already in flight makes the
        // previous restore chain ambiguous. Fail closed for restoration and preserve only the
        // newly confirmed mutation. The already-sent inverse packet remains modeled separately by
        // equipment authority and cannot be wished away here.
        if (pendingRestore != null) {
            clear();
        }

        RestorationCheckpoint previous = checkpoints.peekLast();
        if (previous instanceof RestorationCheckpoint.Hotbar priorHotbar
            && next instanceof RestorationCheckpoint.Hotbar following
            && following.originalSelectedIndex() == priorHotbar.protectionHotbarIndex()
            && following.originalSelectedBefore().sameContents(priorHotbar.protectionAfter())) {
            checkpoints.removeLast();
            checkpoints.addLast(new RestorationCheckpoint.Hotbar(
                priorHotbar.originalSelectedIndex(),
                following.protectionHotbarIndex(),
                priorHotbar.originalSelectedBefore(),
                following.protectionAfter(),
                following.confirmedAtServerTick()
            ));
        } else {
            if (checkpoints.size() >= MAX_RESTORATION_DEPTH) {
                // Restoration is convenience-only. If an unexpectedly deep chain forms, discard
                // older restore intent instead of retaining unbounded stale inventory state.
                checkpoints.clear();
            }
            checkpoints.addLast(next);
        }

        pendingRestore = null;
        holdLease.invalidate();
        leaseNeedsRequirement = true;
        armedUserIntentGeneration = ManualUserIntentTracker.global().generation();
        userIntentInvalidatedRestoreChain = false;
    }

    public boolean hasPendingRestoration() {
        return !checkpoints.isEmpty();
    }

    public long releaseNotBeforeServerTick() {
        return holdLease.releaseNotBeforeServerTick();
    }

    public void abort() {
        clear();
    }

    public Optional<ExecutionCommand> update(
        boolean enabled,
        boolean lethalThreatStillPending,
        boolean survivalActionActive,
        ExecutionContext context
    ) {
        return update(enabled, lethalThreatStillPending, survivalActionActive, 0L, false, context);
    }

    /**
     * Extended form used once pop-generation evidence is available. Until that reconciler is
     * present, callers use generation zero and {@code unprocessedPop=false}; keeping the parameter
     * here prevents Task 3 from inventing a second restoration latch.
     */
    public Optional<ExecutionCommand> update(
        boolean enabled,
        boolean lethalThreatStillPending,
        boolean survivalActionActive,
        long popGeneration,
        boolean unprocessedPop,
        ExecutionContext context
    ) {
        Objects.requireNonNull(context, "context");
        if (!enabled) {
            clear();
            return Optional.empty();
        }

        RestorationCheckpoint checkpoint = checkpoints.peekLast();
        if (checkpoint == null) return Optional.empty();

        long currentUserIntentGeneration = ManualUserIntentTracker.global().generation();
        if (currentUserIntentGeneration != armedUserIntentGeneration) {
            if (pendingRestore == null) {
                // User intent arrived after this convenience-only restore checkpoint was armed.
                // Do not override it simply because the older server-authoritative protection slot
                // is still the conservative branch while the user's packet is in flight.
                clear();
                return Optional.empty();
            }
            // An inverse packet is already on the wire and cannot be unsent. Keep observing it so
            // packet-order authority remains honest, but never continue the stale restore chain.
            userIntentInvalidatedRestoreChain = true;
        }

        if (leaseNeedsRequirement) {
            holdLease.require(ProtectionHoldLease.ProtectionRequirement.rescuePending(), context.timing(), popGeneration);
            leaseNeedsRequirement = false;
        }
        if (lethalThreatStillPending) {
            holdLease.require(ProtectionHoldLease.ProtectionRequirement.lethalThreat(), context.timing(), popGeneration);
        }
        if (survivalActionActive || context.serverUsingItem()) {
            holdLease.require(ProtectionHoldLease.ProtectionRequirement.rescuePending(), context.timing(), popGeneration);
        }
        if (unprocessedPop) {
            holdLease.require(
                new ProtectionHoldLease.ProtectionRequirement(false, false, false, false, true),
                context.timing(),
                popGeneration
            );
        }

        if (pendingRestore != null) {
            // An inverse packet is already on the wire. It cannot be cancelled, but it also cannot
            // count as evidence that the world has remained safely quiet for a later restore.
            holdLease.observeSafe(
                new ProtectionHoldLease.SafeEvidence(
                    !lethalThreatStillPending,
                    !lethalThreatStillPending,
                    true,
                    false,
                    !unprocessedPop,
                    true
                ),
                context.timing(),
                popGeneration
            );
            observePending(checkpoint, context);
            return Optional.empty();
        }

        if (!checkpointStillIntact(checkpoint, context)) {
            clear();
            return Optional.empty();
        }

        if (lethalThreatStillPending || survivalActionActive || context.serverUsingItem() || unprocessedPop) {
            return Optional.empty();
        }

        holdLease.observeSafe(ProtectionHoldLease.SafeEvidence.clean(), context.timing(), popGeneration);
        if (holdLease.blocksRestoration(context.currentServerTick())) return Optional.empty();

        if (checkpoint instanceof RestorationCheckpoint.Hotbar hotbar) {
            long confirmBy = Math.max(context.currentServerTick(), context.timing().nextPacketProcessingWindow().latest());
            pendingRestore = new PendingRestore.Hotbar(confirmBy);
            holdLease.require(ProtectionHoldLease.ProtectionRequirement.rescuePending(), context.timing(), popGeneration);
            return Optional.of(new ExecutionCommand.SelectHotbar(hotbar.originalSelectedIndex()));
        }

        if (checkpoint instanceof RestorationCheckpoint.RoutedContainer routed) {
            if (context.menu().containerId() != routed.containerId()
                || context.menu().menuSlotForInventoryIndex(routed.sourceInventoryIndex()).orElse(-1) != routed.sourceMenuSlot()) {
                clear();
                return Optional.empty();
            }
            pendingRestore = new PendingRestore.RoutedContainer(
                context.menu().stateId(),
                Math.max(context.currentServerTick(), context.timing().nextPacketProcessingWindow().latest())
            );
            holdLease.require(ProtectionHoldLease.ProtectionRequirement.rescuePending(), context.timing(), popGeneration);
            return Optional.of(new ExecutionCommand.SwapMenuSlot(
                routed.containerId(),
                context.menu().stateId(),
                routed.sourceMenuSlot(),
                routed.button()
            ));
        }

        RestorationCheckpoint.Container container = (RestorationCheckpoint.Container) checkpoint;
        EmergencyInventoryTransaction restoring = container.transaction().attemptRestore(false);
        if (restoring.state() != EmergencyInventoryTransaction.State.RESTORING) {
            clear();
            return Optional.empty();
        }
        pendingRestore = new PendingRestore.Container(
            context.menu().stateId(),
            Math.max(context.currentServerTick(), context.timing().nextPacketProcessingWindow().latest())
        );
        holdLease.require(ProtectionHoldLease.ProtectionRequirement.rescuePending(), context.timing(), popGeneration);
        return Optional.of(new ExecutionCommand.SwapMenuSlot(
            context.menu().containerId(),
            context.menu().stateId(),
            restoring.route().sourceMenuSlot(),
            restoring.route().button()
        ));
    }

    private boolean checkpointStillIntact(RestorationCheckpoint checkpoint, ExecutionContext context) {
        if (checkpoint instanceof RestorationCheckpoint.Hotbar hotbar) {
            if (context.inventory().selectedHotbarIndex() == hotbar.originalSelectedIndex()) return false;
            if (context.inventory().selectedHotbarIndex() != hotbar.protectionHotbarIndex()) return false;
            return same(context, hotbar.originalSelectedBefore().inventoryIndex(), hotbar.originalSelectedBefore())
                && same(context, hotbar.protectionHotbarIndex(), hotbar.protectionAfter());
        }

        if (checkpoint instanceof RestorationCheckpoint.RoutedContainer routed) {
            if (context.menu().containerId() != routed.containerId()) return false;
            if (context.menu().menuSlotForInventoryIndex(routed.sourceInventoryIndex()).orElse(-1) != routed.sourceMenuSlot()) {
                return false;
            }
            if (routed.destinationInventoryIndex() >= 0 && routed.destinationInventoryIndex() <= 8
                && context.inventory().selectedHotbarIndex() != routed.destinationInventoryIndex()) {
                return false;
            }
            return same(context, routed.sourceInventoryIndex(), routed.sourceAfter())
                && same(context, routed.destinationInventoryIndex(), routed.destinationAfter());
        }

        RestorationCheckpoint.Container container = (RestorationCheckpoint.Container) checkpoint;
        if (context.menu().containerId() != container.transaction().containerId()) return false;
        if (container.transaction().state() != EmergencyInventoryTransaction.State.CONFIRMED
            || !container.transaction().canRestoreOriginalDestinationStack()) {
            return false;
        }
        return same(context, container.sourceInventoryIndex(), container.transaction().destinationBefore())
            && same(context, container.destinationInventoryIndex(), container.transaction().sourceBefore());
    }

    private void observePending(RestorationCheckpoint checkpoint, ExecutionContext context) {
        if (checkpoint instanceof RestorationCheckpoint.Hotbar hotbar
            && pendingRestore instanceof PendingRestore.Hotbar pending) {
            if (!same(context, hotbar.originalSelectedBefore().inventoryIndex(), hotbar.originalSelectedBefore())
                || !same(context, hotbar.protectionHotbarIndex(), hotbar.protectionAfter())) {
                clear();
                return;
            }
            int selected = context.inventory().selectedHotbarIndex();
            if (selected == hotbar.originalSelectedIndex()) {
                completeCurrentRestore();
                return;
            }
            if (selected != hotbar.protectionHotbarIndex() || context.currentServerTick() > pending.confirmByServerTick()) {
                clear();
            }
            return;
        }

        if (checkpoint instanceof RestorationCheckpoint.RoutedContainer routed
            && pendingRestore instanceof PendingRestore.RoutedContainer pending) {
            if (context.menu().containerId() != routed.containerId()) {
                clear();
                return;
            }
            if (context.menu().stateId() != pending.sentStateId()) {
                boolean restored = same(context, routed.sourceInventoryIndex(), routed.destinationAfter())
                    && same(context, routed.destinationInventoryIndex(), routed.originalDestinationBefore());
                if (restored) completeCurrentRestore();
                else clear();
                return;
            }
            if (context.currentServerTick() > pending.confirmByServerTick()) clear();
            return;
        }

        if (!(checkpoint instanceof RestorationCheckpoint.Container container)
            || !(pendingRestore instanceof PendingRestore.Container pending)) {
            clear();
            return;
        }
        if (context.menu().containerId() != container.transaction().containerId()) {
            clear();
            return;
        }
        if (context.menu().stateId() != pending.sentStateId()) {
            boolean restored = same(context, container.sourceInventoryIndex(), container.transaction().sourceBefore())
                && same(context, container.destinationInventoryIndex(), container.transaction().destinationBefore());
            if (restored) completeCurrentRestore();
            else clear();
            return;
        }
        if (context.currentServerTick() > pending.confirmByServerTick()) clear();
    }

    private static boolean same(ExecutionContext context, int inventoryIndex, InventorySlotSnapshot expected) {
        return context.inventory().slot(inventoryIndex).map(slot -> slot.sameContents(expected)).orElse(false);
    }

    private void completeCurrentRestore() {
        if (userIntentInvalidatedRestoreChain) {
            clear();
            return;
        }
        if (!checkpoints.isEmpty()) checkpoints.removeLast();
        pendingRestore = null;
        holdLease.invalidate();
        leaseNeedsRequirement = !checkpoints.isEmpty();
    }

    private void clear() {
        checkpoints.clear();
        pendingRestore = null;
        holdLease.invalidate();
        leaseNeedsRequirement = false;
        armedUserIntentGeneration = ManualUserIntentTracker.global().generation();
        userIntentInvalidatedRestoreChain = false;
    }

    private sealed interface PendingRestore {
        record Hotbar(long confirmByServerTick) implements PendingRestore {}
        record RoutedContainer(int sentStateId, long confirmByServerTick) implements PendingRestore {}
        record Container(int sentStateId, long confirmByServerTick) implements PendingRestore {}
    }
}
