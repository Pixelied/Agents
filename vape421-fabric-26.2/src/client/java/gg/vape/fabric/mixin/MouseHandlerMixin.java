package gg.vape.fabric.mixin;

import gg.vape.fabric.input.FabricInputBridge;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void vape421$onButton(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
        if (FabricInputBridge.onMouseButton(info.button(), action, info.modifiers())) ci.cancel();
    }

    @Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
    private void vape421$onScroll(long handle, double xOffset, double yOffset, CallbackInfo ci) {
        if (FabricInputBridge.onScroll(xOffset, yOffset)) ci.cancel();
    }
}
