package dev.pixelied.survival.inventory;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class MinecraftInventorySnapshotFactory {
    public InventorySnapshot captureInventory(LocalPlayer player) {
        Objects.requireNonNull(player, "player");
        Inventory inventory = player.getInventory();
        Map<Integer, InventorySlotSnapshot> slots = new LinkedHashMap<>();
        for (int index = 0; index <= 35; index++) {
            slots.put(index, snapshot(index, inventory.getItem(index)));
        }
        slots.put(40, snapshot(40, inventory.getItem(40)));

        ItemStack offhand = inventory.getItem(40);
        boolean activeOffhandShield = player.isUsingItem()
            && player.getUsedItemHand() == InteractionHand.OFF_HAND
            && offhand.get(DataComponents.BLOCKS_ATTACKS) != null;
        return new InventorySnapshot(inventory.getSelectedSlot(), slots, activeOffhandShield);
    }

    public MenuSlotMap captureMenu(LocalPlayer player) {
        Objects.requireNonNull(player, "player");
        AbstractContainerMenu menu = player.containerMenu;
        Inventory inventory = player.getInventory();
        Map<Integer, Integer> inventoryToMenu = new LinkedHashMap<>();
        for (int menuSlot = 0; menuSlot < menu.slots.size(); menuSlot++) {
            Slot slot = menu.slots.get(menuSlot);
            if (slot.container != inventory) continue;
            int inventoryIndex = slot.getContainerSlot();
            if ((inventoryIndex >= 0 && inventoryIndex <= 35) || inventoryIndex == 40) {
                inventoryToMenu.putIfAbsent(inventoryIndex, menuSlot);
            }
        }
        return new MenuSlotMap(menu.containerId, menu.getStateId(), inventoryToMenu);
    }

    private static InventorySlotSnapshot snapshot(int index, ItemStack stack) {
        String key = stack.isEmpty()
            ? "minecraft:air"
            : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return new InventorySlotSnapshot(
            index,
            key,
            stack.getCount(),
            !stack.isEmpty() && stack.get(DataComponents.DEATH_PROTECTION) != null
        );
    }
}
