package gg.vape.fabric.mixin;

import gg.vape.fabric.input.FabricInputBridge;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void vape421$onKeyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
        if (FabricInputBridge.onKey(event.key(), event.scancode(), action, event.modifiers())) ci.cancel();
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void vape421$onCharTyped(long handle, CharacterEvent event, CallbackInfo ci) {
        if (FabricInputBridge.onCharacter(event.codepoint(), event.modifiers())) ci.cancel();
    }
}
