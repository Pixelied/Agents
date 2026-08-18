package dev.adrien.crystaloptimizer.client.execution;

import dev.adrien.crystaloptimizer.execution.CommitPhase;
import dev.adrien.crystaloptimizer.execution.InventoryCoordinator;
import dev.adrien.crystaloptimizer.execution.ReservationRequest;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;

public final class HotbarRestocker {
    private final Minecraft minecraft;
    private final InventoryCoordinator reservations;

    public HotbarRestocker(Minecraft minecraft, InventoryCoordinator reservations) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
    }

    public boolean restock(Item item, int hotbarSlot, CommitPhase phase) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(phase, "phase");
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            throw new IllegalArgumentException("hotbarSlot must be in [0, 8]");
        }
        if (phase == CommitPhase.COMMITTED || phase == CommitPhase.RECONCILING) {
            return false;
        }
        if (reservedByOtherSystem(hotbarSlot)) {
            return false;
        }

        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.gameMode == null) {
            return false;
        }
        if (player.getInventory().getItem(hotbarSlot).is(item)) {
            return true;
        }

        for (int inventoryIndex = 9; inventoryIndex <= 35; inventoryIndex++) {
            if (!player.getInventory().getItem(inventoryIndex).is(item)) {
                continue;
            }
            minecraft.gameMode.handleInventoryMouseClick(
                player.inventoryMenu.containerId,
                inventoryIndex,
                hotbarSlot,
                ClickType.SWAP,
                player
            );
            return true;
        }
        return false;
    }

    private boolean reservedByOtherSystem(int hotbarSlot) {
        return reservations.activeReservations().stream().anyMatch(reservation ->
            reservation.request().owner() != ReservationRequest.Owner.AURA
                && reservation.request().hotbarSlots().contains(hotbarSlot)
        );
    }
}
