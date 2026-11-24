package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void vape421$entityJoin(Entity entity, CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventEntityJoinWorld",
                new Class<?>[]{Object.class}, entity)) {
            ci.cancel();
        }
    }
}
