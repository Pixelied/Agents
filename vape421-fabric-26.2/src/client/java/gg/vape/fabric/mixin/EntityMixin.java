package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "setSprinting", at = @At("HEAD"), cancellable = true)
    private void vape421$setSprinting(boolean sprinting, CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventSetSprinting",
                new Class<?>[]{Object.class, boolean.class},
                this, sprinting)) {
            ci.cancel();
        }
    }
}
