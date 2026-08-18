package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Objects;

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

    record PlaceBlock(SurvivalAction.BlockTarget target, SurvivalAction.Hand hand) implements ExecutionCommand {
        public PlaceBlock {
            target = Objects.requireNonNull(target, "target");
            hand = Objects.requireNonNull(hand, "hand");
        }
    }

    record MoveToward(Vec3Snapshot target) implements ExecutionCommand {
        public MoveToward {
            target = Objects.requireNonNull(target, "target");
        }
    }

    record AimAndUseItem(SurvivalAction.Hand hand, Vec3Snapshot target) implements ExecutionCommand {
        public AimAndUseItem {
            hand = Objects.requireNonNull(hand, "hand");
            target = Objects.requireNonNull(target, "target");
        }
    }
}
