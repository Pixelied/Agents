package dev.pixelied.maceswap.mixin;

import dev.pixelied.maceswap.PredictiveMaceSwapClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe vanilla attack attempts (including misses) without cancelling or modifying input. */
@Mixin(Minecraft.class)
public abstract class MinecraftAttackMixin {
    @Inject(method = "startAttack", at = @At("RETURN"))
    private void predictiveMaceSwap$afterAttack(CallbackInfoReturnable<Boolean> result) {
        PredictiveMaceSwapClient.recordAttackAttempt();
    }
}
