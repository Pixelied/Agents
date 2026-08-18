package dev.pixelied.survival.execution;

import dev.pixelied.survival.planner.SurvivalAction;

public sealed interface ExecutionCommand {
    record SelectHotbar(int hotbarIndex) implements ExecutionCommand {
        public SelectHotbar {
            if (hotbarIndex < 0 || hotbarIndex > 8) {
                throw new IllegalArgumentException("hotbarIndex must be in [0, 8]");
            }
        }
    }

    record SwapMenuSlot(
        int containerId,
        int stateId,
        int sourceMenuSlot,
        int button
    ) implements ExecutionCommand {
        public SwapMenuSlot {
            if (containerId < 0 || stateId < 0 || sourceMenuSlot < 0) {
                throw new IllegalArgumentException("container/state/source slot must be non-negative");
            }
            if (!((button >= 0 && button <= 8) || button == 40)) {
                throw new IllegalArgumentException("SWAP button must be hotbar 0..8 or offhand 40");
            }
        }
    }

    record UseItem(SurvivalAction.Hand hand) implements ExecutionCommand {
        public UseItem {
            if (hand == null) throw new NullPointerException("hand");
        }
    }
}
