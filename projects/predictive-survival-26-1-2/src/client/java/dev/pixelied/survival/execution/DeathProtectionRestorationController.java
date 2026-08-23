package dev.pixelied.survival.execution;

import dev.pixelied.survival.inventory.EmergencyInventoryTransaction;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;

import java.util.Objects;
import java.util.Optional;

/**
 * Reverses only a server-confirmed emergency hand mutation after the lethal window has
 * remained gone through an estimated server processing window. Any disagreement aborts.
 */
public final class DeathProtectionRestorationController {
    private RestorationCheckpoint checkpoint;
    private PendingRestore pendingRestore;
    private long safeUntilServerTick = -1L;

    public void arm(RestorationCheckpoint checkpoint) {
        RestorationCheckpoint next = Objects.requireNonNull(checkpoint, "checkpoint");
        if (pendingRestore == null
            && this.checkpoint instanceof RestorationCheckpoint.Hotbar previous
            && next instanceof RestorationCheckpoint.Hotbar following
            && following.originalSelectedIndex() == previous.protectionHotbarIndex()
            && following.originalSelectedBefore().sameContents(previous.protectionAfter())) {
            this.checkpoint = new RestorationCheckpoint.Hotbar(
                previous.originalSelectedIndex(),
                following.protectionHotbarIndex(),
                previous.originalSelectedBefore(),
                following.protectionAfter(),
                following.confirmedAtServerTick()
            );
        } else {
            this.checkpoint = next;
        }
        this.pendingRestore = null;
        this.safeUntilServerTick = -1L;
    }

    public boolean hasPendingRestoration() {
        return checkpoint != null;
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
        if (checkpoint == null) return Optional.empty();

        if (pendingRestore != null) {
            observePending(context);
            return Optional.empty();
        }

        if (!checkpointStillIntact(context)) {
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

    private boolean checkpointStillIntact(ExecutionContext context) {
        if (checkpoint instanceof RestorationCheckpoint.Hotbar hotbar) {
            if (context.inventory().selectedHotbarIndex() == hotbar.originalSelectedIndex()) {
                clear();
                return false;
            }
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

    private void observePending(ExecutionContext context) {
        if (checkpoint instanceof RestorationCheckpoint.Hotbar hotbar
            && pendingRestore instanceof PendingRestore.Hotbar pending) {
            if (!same(context, hotbar.originalSelectedBefore().inventoryIndex(), hotbar.originalSelectedBefore())
                || !same(context, hotbar.protectionHotbarIndex(), hotbar.protectionAfter())) {
                clear();
                return;
            }
            int selected = context.inventory().selectedHotbarIndex();
            if (selected == hotbar.originalSelectedIndex()) {
                clear();
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
                clear();
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
            clear();
            return;
        }
        if (context.currentServerTick() > pending.confirmByServerTick()) clear();
    }

    private static boolean same(ExecutionContext context, int inventoryIndex, InventorySlotSnapshot expected) {
        return context.inventory().slot(inventoryIndex).map(slot -> slot.sameContents(expected)).orElse(false);
    }

    private void clear() {
        checkpoint = null;
        pendingRestore = null;
        safeUntilServerTick = -1L;
    }

    private sealed interface PendingRestore {
        record Hotbar(long confirmByServerTick) implements PendingRestore {}
        record RoutedContainer(int sentStateId, long confirmByServerTick) implements PendingRestore {}
        record Container(int sentStateId, long confirmByServerTick) implements PendingRestore {}
    }
}
