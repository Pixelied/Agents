package dev.adrien.naturalaim.mixin;

import dev.adrien.naturalaim.NaturalAimClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void naturalaim$afterVanillaMouseTurn(double movementTime, CallbackInfo ci) {
        NaturalAimClient.engine().onMouseTurn(Minecraft.getInstance());
    }
}
