package gg.vape.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import gg.vape.fabric.RecoveredEventDispatcher;
import gg.vape.fabric.movement.RecoveredMovementAdapter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
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

    @WrapMethod(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V")
    private void vape421$move(MoverType moverType, Vec3 movement, Operation<Void> original) {
        if (!((Object) this instanceof LocalPlayer)) {
            original.call(moverType, movement);
            return;
        }

        RecoveredMovementAdapter.PreResult result = RecoveredMovementAdapter.pre(movement);
        if (result.cancelled()) return;

        Vec3 effectiveMovement = result.movement();
        original.call(moverType, effectiveMovement);
        RecoveredMovementAdapter.post(effectiveMovement);
    }
}
