package dev.pixelied.survival.inventory;

import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Objects;

/**
 * Server-valid route that moves one exact survival stack into a usable hand before activation.
 * Item identity always includes the component fingerprint so two stacks with the same registry key
 * cannot be substituted for one another.
 */
public sealed interface SurvivalItemRoute {
    SurvivalAction.Hand destinationHand();
    String itemKey();
    int componentFingerprint();
    int requiredServerTicks();

    record AlreadyHeld(
        SurvivalAction.Hand destinationHand,
        String itemKey,
        int componentFingerprint
    ) implements SurvivalItemRoute {
        public AlreadyHeld {
            destinationHand = Objects.requireNonNull(destinationHand, "destinationHand");
            itemKey = requireItemKey(itemKey);
        }

        @Override public int requiredServerTicks() { return 0; }
    }

    record HotbarSelect(
        int hotbarIndex,
        SurvivalAction.Hand destinationHand,
        String itemKey,
        int componentFingerprint
    ) implements SurvivalItemRoute {
        public HotbarSelect {
            if (hotbarIndex < 0 || hotbarIndex > 8) throw new IllegalArgumentException("hotbarIndex must be in [0, 8]");
            destinationHand = Objects.requireNonNull(destinationHand, "destinationHand");
            if (destinationHand != SurvivalAction.Hand.MAIN_HAND) {
                throw new IllegalArgumentException("hotbar selection can only target the main hand");
            }
            itemKey = requireItemKey(itemKey);
        }

        @Override public int requiredServerTicks() { return 1; }
    }

    record ContainerSwap(
        int sourceInventoryIndex,
        int sourceMenuSlot,
        int destinationInventoryIndex,
        int button,
        SurvivalAction.Hand destinationHand,
        String itemKey,
        int componentFingerprint
    ) implements SurvivalItemRoute {
        public ContainerSwap {
            if (sourceInventoryIndex < 0 || sourceInventoryIndex > 40 || sourceMenuSlot < 0) {
                throw new IllegalArgumentException("source inventory/menu slot must be valid");
            }
            if (destinationInventoryIndex < 0 || destinationInventoryIndex > 40) {
                throw new IllegalArgumentException("destination inventory slot must be valid");
            }
            if (!((button >= 0 && button <= 8) || button == 40)) {
                throw new IllegalArgumentException("swap button must be hotbar 0..8 or offhand 40");
            }
            destinationHand = Objects.requireNonNull(destinationHand, "destinationHand");
            itemKey = requireItemKey(itemKey);
        }

        @Override public int requiredServerTicks() { return 1; }
    }

    private static String requireItemKey(String itemKey) {
        String key = Objects.requireNonNull(itemKey, "itemKey");
        if (key.isBlank()) throw new IllegalArgumentException("itemKey must not be blank");
        return key;
    }
}
