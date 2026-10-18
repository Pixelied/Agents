package dev.adrien.togglesneak.mixin;

import dev.adrien.togglesneak.ToggleSneakClient;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    private void togglesneak$onTick(CallbackInfo ci) {
        ToggleSneakClient.onClientTick((Minecraft) (Object) this);
    }
}
