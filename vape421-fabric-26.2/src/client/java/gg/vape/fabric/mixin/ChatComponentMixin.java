package gg.vape.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import gg.vape.fabric.chat.RecoveredChatAdapter;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    @WrapMethod(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V")
    private void vape421$addMessage(
            Component content,
            @Nullable MessageSignature signature,
            GuiMessageSource source,
            GuiMessageTag tag,
            Operation<Void> original) {
        Component rewritten = RecoveredChatAdapter.rewrite(
                this, content, signature, tag);
        original.call(rewritten, signature, source, tag);
    }
}
