package dev.adrien.crystaloptimizer.client.mixin;

import dev.adrien.crystaloptimizer.client.v2.ClientCombatEventBus;
import dev.adrien.crystaloptimizer.client.v2.ClientRevisionTracker;
import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Inventory.class)
public abstract class LocalInventoryMixin {
    @Inject(method = "setSelectedSlot", at = @At("TAIL"))
    private void crystaloptimizer$selectedSlotChanged(int selected, CallbackInfo ci) {
        crystaloptimizer$publishInventoryChanged();
    }

    @Inject(method = "setSelectedItem", at = @At("TAIL"))
    private void crystaloptimizer$selectedItemChanged(
        ItemStack itemStack,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        crystaloptimizer$publishInventoryChanged();
    }

    @Inject(
        method = "add(ILnet/minecraft/world/item/ItemStack;)Z",
        at = @At("RETURN")
    )
    private void crystaloptimizer$itemAdded(
        int slot,
        ItemStack itemStack,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (cir.getReturnValueZ()) {
            crystaloptimizer$publishInventoryChanged();
        }
    }

    @Inject(
        method = "removeItem(II)Lnet/minecraft/world/item/ItemStack;",
        at = @At("RETURN")
    )
    private void crystaloptimizer$itemRemoved(
        int slot,
        int count,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!cir.getReturnValue().isEmpty()) {
            crystaloptimizer$publishInventoryChanged();
        }
    }

    @Inject(
        method = "removeItem(Lnet/minecraft/world/item/ItemStack;)V",
        at = @At("TAIL")
    )
    private void crystaloptimizer$itemRemovedByReference(ItemStack itemStack, CallbackInfo ci) {
        crystaloptimizer$publishInventoryChanged();
    }

    @Inject(method = "removeItemNoUpdate", at = @At("RETURN"))
    private void crystaloptimizer$itemRemovedWithoutUpdate(
        int slot,
        CallbackInfoReturnable<ItemStack> cir
    ) {
        if (!cir.getReturnValue().isEmpty()) {
            crystaloptimizer$publishInventoryChanged();
        }
    }

    @Inject(method = "setItem", at = @At("TAIL"))
    private void crystaloptimizer$itemSet(int slot, ItemStack itemStack, CallbackInfo ci) {
        crystaloptimizer$publishInventoryChanged();
    }

    private void crystaloptimizer$publishInventoryChanged() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null
            || minecraft.player.getInventory() != (Inventory)(Object)this) {
            return;
        }
        long nowNanos = System.nanoTime();
        long revision = ClientRevisionTracker.instance().markInventoryMutation();
        ClientCombatEventBus.instance().publish(
            new CombatEvent.InventoryChanged(revision, nowNanos)
        );
    }
}
