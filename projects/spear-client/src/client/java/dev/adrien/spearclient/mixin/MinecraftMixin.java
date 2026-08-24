package dev.adrien.spearclient.mixin;

import dev.adrien.spearclient.SpearClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void spearclient$onStartAttack(CallbackInfoReturnable<Boolean> cir) {
        if (SpearClient.instance().controller().onAttackPressed((Minecraft)(Object)this)) {
            cir.setReturnValue(true);
        }
    }
}
