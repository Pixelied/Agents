package dev.adrien.crystaloptimizer.client.execution;

import dev.adrien.crystaloptimizer.execution.HotbarRestockPlanner;
import dev.adrien.crystaloptimizer.execution.InventoryCoordinator;
import dev.adrien.crystaloptimizer.execution.InventoryStackView;
import dev.adrien.crystaloptimizer.execution.ReservationRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;

public final class HotbarRestocker {
    private final Minecraft minecraft;
    private final InventoryCoordinator reservations;
    private final HotbarRestockPlanner planner = new HotbarRestockPlanner();

    public HotbarRestocker(Minecraft minecraft, InventoryCoordinator reservations) {
        this.minecraft = Objects.requireNonNull(minecraft, "minecraft");
        this.reservations = Objects.requireNonNull(reservations, "reservations");
    }

    public boolean restockOne(LocalPlayer player) {
        Objects.requireNonNull(player, "player");
        if (minecraft.gameMode == null
            || minecraft.screen != null
            || player.containerMenu != player.inventoryMenu) {
            return false;
        }

        List<ItemStack> items = player.getInventory().getNonEquipmentItems();
        List<InventoryStackView> stacks = new ArrayList<>();
        for (int slot = 0; slot < items.size(); slot++) {
            ItemStack stack = items.get(slot);
            if (!stack.isEmpty()) {
                stacks.add(new InventoryStackView(slot, stack.getItem(), stack.getCount()));
            }
        }

        var decision = planner.choose(stacks);
        if (decision.isEmpty() || reservedByOtherSystem(decision.orElseThrow().hotbarSlot())) {
            return false;
        }
        var restock = decision.orElseThrow();
        minecraft.gameMode.handleContainerInput(
            player.containerMenu.containerId,
            restock.sourceInventorySlot(),
            restock.hotbarSlot(),
            ContainerInput.SWAP,
            player
        );
        return true;
    }

    private boolean reservedByOtherSystem(int hotbarSlot) {
        return reservations.activeReservations().stream().anyMatch(reservation ->
            reservation.request().owner() != ReservationRequest.Owner.AURA
                && reservation.request().hotbarSlots().contains(hotbarSlot)
        );
    }
}
