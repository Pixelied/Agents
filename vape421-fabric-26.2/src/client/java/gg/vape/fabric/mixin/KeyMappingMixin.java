package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyMapping.class)
abstract class KeyMappingMixin {
    @Inject(method = "setDown", at = @At("HEAD"), cancellable = true)
    private void vape421$keyBindingState(boolean pressed, CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventKeyBindingState",
                new Class<?>[]{Object.class, boolean.class},
                this, pressed)) {
            ci.cancel();
        }
    }
}
