package dev.pixelied.survival.inventory;

import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.MinecraftEquipmentAdapter;
import dev.pixelied.survival.damage.MinecraftBlockingAdapter;
import dev.pixelied.survival.damage.MinecraftDeathProtectionAdapter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.world.item.consume_effects.ApplyStatusEffectsConsumeEffect;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class MinecraftInventorySnapshotFactory {
    private final MinecraftEquipmentAdapter equipmentAdapter = new MinecraftEquipmentAdapter();

    public InventorySnapshot captureInventory(LocalPlayer player) {
        Objects.requireNonNull(player, "player");
        Inventory inventory = player.getInventory();
        Map<Integer, InventorySlotSnapshot> slots = new LinkedHashMap<>();
        for (int index = 0; index <= 35; index++) {
            slots.put(index, snapshot(index, inventory.getItem(index), player));
        }
        slots.put(40, snapshot(40, inventory.getItem(40), player));

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

    private InventorySlotSnapshot snapshot(int index, ItemStack stack, LocalPlayer player) {
        String key = stack.isEmpty()
            ? "minecraft:air"
            : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        var protectionItem = stack.isEmpty()
            ? java.util.Optional.<dev.pixelied.survival.damage.DeathProtectionSnapshot.ProtectionItem>empty()
            : MinecraftDeathProtectionAdapter.snapshot(stack);
        var blockingProfile = MinecraftBlockingAdapter.profile(stack);
        boolean blockingOnCooldown = blockingProfile.isPresent() && player.getCooldowns().isOnCooldown(stack);
        return new InventorySlotSnapshot(
            index,
            key,
            stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack),
            stack.getCount(),
            protectionItem.isPresent(),
            consumableCapability(stack, player),
            equippableCapability(stack, player),
            blockingProfile,
            protectionItem,
            blockingOnCooldown
        );
    }

    private static Optional<ConsumableSurvivalSnapshot> consumableCapability(ItemStack stack, LocalPlayer player) {
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (stack.isEmpty() || consumable == null) return Optional.empty();

        List<EffectInstanceSnapshot> guaranteedEffects = new ArrayList<>();
        for (var consumeEffect : consumable.onConsumeEffects()) {
            if (!(consumeEffect instanceof ApplyStatusEffectsConsumeEffect apply)) continue;
            if (Float.compare(apply.probability(), 1f) != 0) continue;
            for (MobEffectInstance effect : apply.effects()) {
                guaranteedEffects.add(new EffectInstanceSnapshot(
                    effect.getEffect().getRegisteredName(),
                    effect.getDuration(),
                    effect.getAmplifier()
                ));
            }
        }

        return Optional.of(new ConsumableSurvivalSnapshot(
            consumable.consumeTicks(),
            consumable.canConsume(player, stack),
            guaranteedEffects
        ));
    }

    private Optional<EquippableSurvivalSnapshot> equippableCapability(ItemStack stack, LocalPlayer player) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (stack.isEmpty() || equippable == null || !isHumanoidArmor(equippable.slot())) {
            return Optional.empty();
        }

        EquipmentSlot slot = equippable.slot();
        ItemStack current = player.getItemBySlot(slot);
        boolean armorLocked = EnchantmentHelper.has(current, EnchantmentEffectComponents.PREVENT_ARMOR_CHANGE)
            && !player.isCreative();
        boolean usable = equippable.swappable()
            && player.canUseSlot(slot)
            && equippable.canBeEquippedBy(player.typeHolder())
            && !armorLocked
            && !ItemStack.isSameItemSameComponents(stack, current);

        return Optional.of(new EquippableSurvivalSnapshot(
            equipmentAdapter.armorPiece(stack, slot, equippable.damageOnHurt()),
            usable
        ));
    }

    private static boolean isHumanoidArmor(EquipmentSlot slot) {
        return slot == EquipmentSlot.FEET
            || slot == EquipmentSlot.LEGS
            || slot == EquipmentSlot.CHEST
            || slot == EquipmentSlot.HEAD;
    }
}
