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
    boolean shieldAngleValid,
    ServerStateEvidenceSnapshot serverStateEvidence
) {
    public ExecutionContext(
        InventorySnapshot inventory,
        MenuSlotMap menu,
        TimingSnapshot timing,
        long currentServerTick,
        boolean serverUsingItem,
        SurvivalAction.Hand usingHand,
        int serverUseTicks,
        boolean shieldAngleValid
    ) {
        this(
            inventory,
            menu,
            timing,
            currentServerTick,
            serverUsingItem,
            usingHand,
            serverUseTicks,
            shieldAngleValid,
            MinecraftServerStateEvidence.snapshot()
        );
    }

    public ExecutionContext {
        inventory = Objects.requireNonNull(inventory, "inventory");
        menu = Objects.requireNonNull(menu, "menu");
        timing = Objects.requireNonNull(timing, "timing");
        serverStateEvidence = Objects.requireNonNull(serverStateEvidence, "serverStateEvidence");
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
