package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void vape421$preAttack(Player player, Entity target, CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire("gg.vape.event.impl.EventPreAttack", new Class<?>[]{Object.class}, target)) ci.cancel();
    }
    @Inject(method = "attack", at = @At("TAIL"))
    private void vape421$postAttack(Player player, Entity target, CallbackInfo ci) {
        RecoveredEventDispatcher.fire("gg.vape.event.impl.EventPostAttack", new Class<?>[]{Object.class}, target);
    }
    @Inject(method = "useItem", at = @At("HEAD"), cancellable = true)
    private void vape421$useItem(Player player, InteractionHand hand, CallbackInfoReturnable<InteractionResult> cir) {
        if (RecoveredEventDispatcher.fire("gg.vape.mapping.PlayerUseItemCallback", new Class<?>[]{Object.class,Object.class}, player, hand)) {
            cir.setReturnValue(InteractionResult.PASS);
        }
    }
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vape421$controllerTick(CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire("gg.vape.event.impl.EventBedBreakerUpdate", new Class<?>[0])) ci.cancel();
    }
    @Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
    private void vape421$windowClick(int containerId, int slotId, int mouseButton, ClickType clickType, Player player, CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire("gg.vape.event.impl.EventWindowClick", new Class<?>[]{Object.class}, this)) ci.cancel();
    }
}
