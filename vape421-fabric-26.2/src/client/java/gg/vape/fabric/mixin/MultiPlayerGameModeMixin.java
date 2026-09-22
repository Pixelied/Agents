package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPlayerGameMode.class)
abstract class MultiPlayerGameModeMixin {
    @Inject(method = "attack", at = @At("HEAD"), cancellable = true)
    private void vape421$preAttack(Player player, Entity target, CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire("gg.vape.event.impl.EventPreAttack",
                new Class<?>[]{Object.class}, target)) ci.cancel();
    }

    @Inject(method = "attack", at = @At("TAIL"))
    private void vape421$postAttack(Player player, Entity target, CallbackInfo ci) {
        RecoveredEventDispatcher.fire("gg.vape.event.impl.EventPostAttack",
                new Class<?>[]{Object.class}, target);
    }
}
