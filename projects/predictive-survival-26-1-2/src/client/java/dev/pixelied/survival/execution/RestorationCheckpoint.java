package dev.pixelied.survival.execution;

import dev.pixelied.survival.inventory.EmergencyInventoryTransaction;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;

import java.util.Objects;

public sealed interface RestorationCheckpoint {
    long confirmedAtServerTick();

    record Hotbar(
        int originalSelectedIndex,
        int protectionHotbarIndex,
        InventorySlotSnapshot originalSelectedBefore,
        InventorySlotSnapshot protectionAfter,
        long confirmedAtServerTick
    ) implements RestorationCheckpoint {
        public Hotbar {
            if (originalSelectedIndex < 0 || originalSelectedIndex > 8
                || protectionHotbarIndex < 0 || protectionHotbarIndex > 8) {
                throw new IllegalArgumentException("hotbar indices must be in [0, 8]");
            }
            originalSelectedBefore = Objects.requireNonNull(originalSelectedBefore, "originalSelectedBefore");
            protectionAfter = Objects.requireNonNull(protectionAfter, "protectionAfter");
            if (confirmedAtServerTick < 0) throw new IllegalArgumentException("confirmedAtServerTick must be non-negative");
        }
    }

    /**
     * Generic reversible SWAP route for an emergency item moved from the player inventory into a
     * hand slot. sourceAfter must still contain the exact stack displaced from the destination;
     * destinationAfter is the authoritative post-action state (it may be empty after consumption).
     */
    record RoutedContainer(
        int containerId,
        int sourceInventoryIndex,
        int sourceMenuSlot,
        int destinationInventoryIndex,
        int button,
        InventorySlotSnapshot originalDestinationBefore,
        InventorySlotSnapshot sourceAfter,
        InventorySlotSnapshot destinationAfter,
        int confirmedMenuStateId,
        long confirmedAtServerTick
    ) implements RestorationCheckpoint {
        public RoutedContainer {
            if (containerId < 0 || sourceMenuSlot < 0) {
                throw new IllegalArgumentException("container/menu slot must be non-negative");
            }
            if (sourceInventoryIndex < 0 || sourceInventoryIndex > 40
                || destinationInventoryIndex < 0 || destinationInventoryIndex > 40) {
                throw new IllegalArgumentException("inventory indices must be in [0, 40]");
            }
            if (!((button >= 0 && button <= 8) || button == 40)) {
                throw new IllegalArgumentException("swap button must be hotbar 0..8 or offhand 40");
            }
            originalDestinationBefore = Objects.requireNonNull(originalDestinationBefore, "originalDestinationBefore");
            sourceAfter = Objects.requireNonNull(sourceAfter, "sourceAfter");
            destinationAfter = Objects.requireNonNull(destinationAfter, "destinationAfter");
            if (originalDestinationBefore.inventoryIndex() != destinationInventoryIndex
                || sourceAfter.inventoryIndex() != sourceInventoryIndex
                || destinationAfter.inventoryIndex() != destinationInventoryIndex) {
                throw new IllegalArgumentException("checkpoint stack snapshots must match their inventory slots");
            }
            if (confirmedMenuStateId < 0 || confirmedAtServerTick < 0) {
                throw new IllegalArgumentException("confirmed menu state/tick must be non-negative");
            }
        }
    }

    record Container(
        EmergencyInventoryTransaction transaction,
        int sourceInventoryIndex,
        int destinationInventoryIndex,
        int confirmedMenuStateId,
        long confirmedAtServerTick
    ) implements RestorationCheckpoint {
        public Container {
            transaction = Objects.requireNonNull(transaction, "transaction");
            if (transaction.state() != EmergencyInventoryTransaction.State.CONFIRMED) {
                throw new IllegalArgumentException("container checkpoint requires a confirmed emergency transaction");
            }
            if (sourceInventoryIndex < 0 || sourceInventoryIndex > 40
                || destinationInventoryIndex < 0 || destinationInventoryIndex > 40) {
                throw new IllegalArgumentException("inventory indices must be in [0, 40]");
            }
            if (confirmedMenuStateId < 0 || confirmedAtServerTick < 0) {
                throw new IllegalArgumentException("confirmed menu state/tick must be non-negative");
            }
        }
    }
}
