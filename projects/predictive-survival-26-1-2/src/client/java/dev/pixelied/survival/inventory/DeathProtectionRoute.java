package dev.pixelied.survival.inventory;

public sealed interface DeathProtectionRoute {
    enum Destination { MAIN_HAND, OFF_HAND }

    record AlreadyInHand(Destination destination) implements DeathProtectionRoute {
    }

    record HotbarSelect(int hotbarIndex) implements DeathProtectionRoute {
        public HotbarSelect {
            if (hotbarIndex < 0 || hotbarIndex > 8) {
                throw new IllegalArgumentException("hotbarIndex must be in [0, 8]");
            }
        }
    }

    record ContainerSwap(int sourceMenuSlot, int button, Destination destination) implements DeathProtectionRoute {
        public ContainerSwap {
            if (sourceMenuSlot < 0) throw new IllegalArgumentException("sourceMenuSlot must be non-negative");
            if (!((button >= 0 && button <= 8) || button == 40)) {
                throw new IllegalArgumentException("SWAP button must be hotbar 0..8 or offhand 40");
            }
            if (destination == Destination.MAIN_HAND && button == 40) {
                throw new IllegalArgumentException("main-hand swap cannot use offhand button");
            }
            if (destination == Destination.OFF_HAND && button != 40) {
                throw new IllegalArgumentException("offhand swap must use button 40");
            }
        }
    }
}
