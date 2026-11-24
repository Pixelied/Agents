package gg.vape.fabric.mixin;

import gg.vape.fabric.RecoveredEventDispatcher;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Gui.class)
abstract class GuiMixin {
    @Inject(method = "setScreen", at = @At("HEAD"))
    private void vape421$onSetScreen(@Nullable Screen screen, CallbackInfo ci) {
        RecoveredEventDispatcher.fire("gg.vape.event.impl.EventGuiOpen",
                new Class<?>[]{Object.class}, screen);
    }
}
