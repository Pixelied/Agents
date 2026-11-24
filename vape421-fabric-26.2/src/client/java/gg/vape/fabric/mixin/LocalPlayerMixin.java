package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
abstract class LocalPlayerMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void vape421$preLocalPlayerTick(CallbackInfo ci) {
        RecoveredEventDispatcher.fire("gg.vape.event.impl.EventPreLocalPlayerTick",
                new Class<?>[]{Object.class}, this);
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void vape421$postLocalPlayerTick(CallbackInfo ci) {
        RecoveredEventDispatcher.fire("gg.vape.event.impl.EventPostLocalPlayerTick",
                new Class<?>[]{Object.class}, this);
    }
}
