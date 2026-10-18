package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void vape421$preEntityUpdate(CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventPreEntityUpdate",
                new Class<?>[]{Object.class}, this)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void vape421$postEntityUpdate(CallbackInfo ci) {
        RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventPostEntityUpdate",
                new Class<?>[]{Object.class}, this);
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    private void vape421$preLivingTravel(Vec3 input, CallbackInfo ci) {
        if (RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventPreLivingTravel",
                new Class<?>[]{Object.class}, this)) {
            ci.cancel();
        }
    }

    @Inject(method = "travel", at = @At("TAIL"))
    private void vape421$postLivingTravel(Vec3 input, CallbackInfo ci) {
        RecoveredEventDispatcher.fire(
                "gg.vape.event.impl.EventPostLivingTravel",
                new Class<?>[]{Object.class}, this);
    }
}
