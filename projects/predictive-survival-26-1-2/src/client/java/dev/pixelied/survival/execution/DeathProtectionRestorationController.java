package dev.pixelied.survival.execution;

import dev.pixelied.survival.inventory.EmergencyInventoryTransaction;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import java.util.Optional;

/**
 * Reverses only server-confirmed emergency hand mutations after the lethal window has
 * remained gone through an estimated server processing window. Mutations are restored in
 * reverse order and any disagreement aborts the entire chain rather than overwriting player state.
 */
public final class DeathProtectionRestorationController {
    private static final int MAX_RESTORATION_DEPTH = 8;

    private final Deque<RestorationCheckpoint> checkpoints = new ArrayDeque<>();
    private PendingRestore pendingRestore;
    private long safeUntilServerTick = -1L;

    public void arm(RestorationCheckpoint checkpoint) {
        RestorationCheckpoint next = Objects.requireNonNull(checkpoint, "checkpoint");

        // A new emergency mutation while an inverse mutation is already in flight makes the
        // previous restore chain ambiguous. Fail closed for restoration and preserve only the
        // newly confirmed mutation.
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
        safeUntilServerTick = -1L;
    }

    public boolean hasPendingRestoration() {
        return !checkpoints.isEmpty();
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
        Objects.requireNonNull(context, "context");
        if (!enabled) {
            clear();
            return Optional.empty();
        }

        RestorationCheckpoint checkpoint = checkpoints.peekLast();
        if (checkpoint == null) return Optional.empty();

        if (pendingRestore != null) {
            observePending(checkpoint, context);
            return Optional.empty();
        }

        if (!checkpointStillIntact(checkpoint, context)) {
            clear();
            return Optional.empty();
        }

        if (lethalThreatStillPending || survivalActionActive || context.serverUsingItem()) {
            safeUntilServerTick = -1L;
            return Optional.empty();
        }

        if (safeUntilServerTick < 0L) {
            safeUntilServerTick = Math.max(
                context.currentServerTick(),
                context.timing().nextPacketProcessingWindow().latest()
            );
        }
        if (context.currentServerTick() < safeUntilServerTick) return Optional.empty();

        if (checkpoint instanceof RestorationCheckpoint.Hotbar hotbar) {
            long confirmBy = Math.max(context.currentServerTick(), context.timing().nextPacketProcessingWindow().latest());
            pendingRestore = new PendingRestore.Hotbar(confirmBy);
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
        if (!checkpoints.isEmpty()) checkpoints.removeLast();
        pendingRestore = null;
        safeUntilServerTick = -1L;
    }

    private void clear() {
        checkpoints.clear();
        pendingRestore = null;
        safeUntilServerTick = -1L;
    }

    private sealed interface PendingRestore {
        record Hotbar(long confirmByServerTick) implements PendingRestore {}
        record RoutedContainer(int sentStateId, long confirmByServerTick) implements PendingRestore {}
        record Container(int sentStateId, long confirmByServerTick) implements PendingRestore {}
    }
}
