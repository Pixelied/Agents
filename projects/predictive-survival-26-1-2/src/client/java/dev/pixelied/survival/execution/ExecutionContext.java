package dev.pixelied.survival.execution;

import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;

import java.util.Objects;

public record ExecutionContext(
    InventorySnapshot inventory,
    MenuSlotMap menu,
    TimingSnapshot timing,
    long currentServerTick,
    boolean serverUsingItem,
    SurvivalAction.Hand usingHand,
    int serverUseTicks,
    boolean shieldAngleValid
) {
    public ExecutionContext {
        inventory = Objects.requireNonNull(inventory, "inventory");
        menu = Objects.requireNonNull(menu, "menu");
        timing = Objects.requireNonNull(timing, "timing");
        if (currentServerTick < 0) throw new IllegalArgumentException("currentServerTick must be non-negative");
        if (serverUseTicks < 0) throw new IllegalArgumentException("serverUseTicks must be non-negative");
        if (!serverUsingItem && usingHand != null) {
            throw new IllegalArgumentException("usingHand must be null when serverUsingItem is false");
        }
        if (serverUsingItem && usingHand == null) {
            throw new IllegalArgumentException("usingHand is required while serverUsingItem is true");
        }
    }
}
