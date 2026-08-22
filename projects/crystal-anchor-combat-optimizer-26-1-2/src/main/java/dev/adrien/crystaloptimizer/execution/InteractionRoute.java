package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.world.InteractionHand;

/** Immutable choice of hand and optional hotbar selection for one interaction. */
public record InteractionRoute(
    InteractionHand hand,
    OptionalInt selectedSlot,
    boolean swapBackRequired,
    double estimatedCostMillis
) {
    public InteractionRoute {
        Objects.requireNonNull(hand, "hand");
        Objects.requireNonNull(selectedSlot, "selectedSlot");
        if (selectedSlot.isPresent()) {
            int slot = selectedSlot.getAsInt();
            if (slot < 0 || slot > 8) {
                throw new IllegalArgumentException("selectedSlot must be in [0, 8]");
            }
            if (hand != InteractionHand.MAIN_HAND) {
                throw new IllegalArgumentException("hotbar selection is only meaningful for main hand");
            }
        }
        if (!Double.isFinite(estimatedCostMillis) || estimatedCostMillis < 0.0) {
            throw new IllegalArgumentException("estimatedCostMillis must be finite and non-negative");
        }
    }

    public static InteractionRoute offhand() {
        return new InteractionRoute(InteractionHand.OFF_HAND, OptionalInt.empty(), false, 0.0);
    }

    public static InteractionRoute selectedMainhand() {
        return new InteractionRoute(InteractionHand.MAIN_HAND, OptionalInt.empty(), false, 0.0);
    }

    public static InteractionRoute selectMainhand(int slot, double estimatedCostMillis) {
        return new InteractionRoute(
            InteractionHand.MAIN_HAND,
            OptionalInt.of(slot),
            true,
            estimatedCostMillis
        );
    }
}
