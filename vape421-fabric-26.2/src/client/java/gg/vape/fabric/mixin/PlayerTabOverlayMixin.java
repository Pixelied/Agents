package gg.vape.fabric.mixin;

import gg.vape.fabric.render.RecoveredTabNameAdapter;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
    @Inject(
            method = "getNameForDisplay(Lnet/minecraft/client/multiplayer/PlayerInfo;)Lnet/minecraft/network/chat/Component;",
            at = @At("HEAD"),
            cancellable = true)
    private void vape421$displayName(
            PlayerInfo playerInfo,
            CallbackInfoReturnable<Component> cir) {
        Object replacement = RecoveredTabNameAdapter.replacement(this, playerInfo);
        if (replacement instanceof Component component) {
            cir.setReturnValue(component);
        }
    }
}
