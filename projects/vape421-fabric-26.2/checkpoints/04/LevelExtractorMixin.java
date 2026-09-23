package gg.vape.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import gg.vape.event.impl.EventPostRenderWorldPass;
import gg.vape.event.impl.EventPreRenderWorldPass;
import gg.vape.fabric.FabricMigrationRuntime;
import gg.vape.fabric.FabricOutlineHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.extract.LevelExtractor;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
    @Inject(method = "extractVisibleEntities", at = @At("HEAD"))
    private void vape421$preVisibleEntityExtraction(Camera camera,
                                                     Frustum frustum,
                                                     DeltaTracker deltaTracker,
                                                     LevelRenderState renderState,
                                                     CallbackInfo ci) {
        if (FabricMigrationRuntime.isCoreStarted()) {
            new EventPreRenderWorldPass().fire();
        }
    }

    @WrapOperation(
            method = "extractVisibleEntities",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;extractEntity(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;"
            )
    )
    private EntityRenderState vape421$extractEntityWithOutline(EntityRenderDispatcher dispatcher,
                                                                Entity entity,
                                                                float partialTicks,
                                                                Operation<EntityRenderState> original) {
        EntityRenderState state = original.call(dispatcher, entity, partialTicks);
        FabricOutlineHooks.apply(entity, state);
        return state;
    }

    @Inject(method = "extractVisibleEntities", at = @At("RETURN"))
    private void vape421$postVisibleEntityExtraction(Camera camera,
                                                      Frustum frustum,
                                                      DeltaTracker deltaTracker,
                                                      LevelRenderState renderState,
                                                      CallbackInfo ci) {
        if (FabricMigrationRuntime.isCoreStarted()) {
            new EventPostRenderWorldPass().fire();
        }
    }
}
