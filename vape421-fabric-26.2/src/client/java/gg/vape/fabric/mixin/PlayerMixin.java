package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
abstract class PlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vape421$prePlayerTick(CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventPrePlayerTick",
                new Class<?>[]{Object.class}, this)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void vape421$postPlayerTick(CallbackInfo ci) {
        RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventPostPlayerTick",
                new Class<?>[]{Object.class}, this);
    }
}
