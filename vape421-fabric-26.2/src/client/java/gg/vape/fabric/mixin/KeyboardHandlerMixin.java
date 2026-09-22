package gg.vape.fabric.mixin;

import gg.vape.fabric.input.FabricInputBridge;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"))
    private void vape421$onKeyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        FabricInputBridge.onKey(event.key(), event.scancode(), action, event.modifiers());
    }
}
