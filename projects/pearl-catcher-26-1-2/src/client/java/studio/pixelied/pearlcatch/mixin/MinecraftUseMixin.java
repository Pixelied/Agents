package studio.pixelied.pearlcatch.mixin;

import studio.pixelied.pearlcatch.LegitSilentUseBridge;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftUseMixin {
    @Inject(method = "startUseItem", at = @At("HEAD"), cancellable = true)
    private void pixeliedStudio$beforeStartUseItem(CallbackInfo ci) {
        if (!LegitSilentUseBridge.beforeVanillaUse((Minecraft) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "startUseItem", at = @At("RETURN"))
    private void pixeliedStudio$afterStartUseItem(CallbackInfo ci) {
        LegitSilentUseBridge.afterVanillaUse((Minecraft) (Object) this);
    }
}
