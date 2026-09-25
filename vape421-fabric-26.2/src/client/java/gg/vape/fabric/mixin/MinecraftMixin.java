package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void vape421$clickMouse(CallbackInfoReturnable<Boolean> cir) {
        if (RecoveredEventDispatcher.fire("gg.vape.event.impl.EventClickMouse", new Class<?>[0])) cir.setReturnValue(false);
    }
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void vape421$rightClickMouse(CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire("gg.vape.event.impl.EventRightClickMouse", new Class<?>[0])) ci.cancel();
    }
    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void vape421$sendClickBlock(boolean breaking, CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire("gg.vape.event.impl.EventSendClickBlockToController", new Class<?>[0])) ci.cancel();
    }
    @Inject(method = "pick", at = @At("TAIL"))
    private void vape421$mouseOverUpdate(float tickDelta, CallbackInfo ci) {
        RecoveredEventDispatcher.fire("gg.vape.event.impl.EventMouseOverUpdate", new Class<?>[]{float.class}, tickDelta);
    }
}
