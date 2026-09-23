package gg.vape.fabric.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import gg.vape.event.impl.EventPostRenderWorldPass;
import gg.vape.event.impl.EventPreRenderWorldPass;
import gg.vape.fabric.FabricMigrationRuntime;
import gg.vape.fabric.FabricOutlineHooks;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Final Minecraft 26.2 keeps visible-entity extraction on LevelRenderer.
 * This replaces the old runtime transformer boundary and also applies Vape's
 * Outline ESP through the native 26.2 entity render-state pipeline.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererEntityExtractionMixin {
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

    @ModifyReturnValue(method = "extractEntity", at = @At("RETURN"))
    private EntityRenderState vape421$applyEntityOutline(EntityRenderState state,
                                                          Entity entity,
                                                          float partialTickTime) {
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
