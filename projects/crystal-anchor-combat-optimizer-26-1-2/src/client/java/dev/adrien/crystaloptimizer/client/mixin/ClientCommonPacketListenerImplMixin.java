package dev.adrien.crystaloptimizer.client.mixin;

import dev.adrien.crystaloptimizer.client.execution.InteractionTimingRecorder;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
    @Inject(method = "send", at = @At("HEAD"))
    private void crystaloptimizer$recordUseItemSequence(Packet<?> packet, CallbackInfo ci) {
        if (packet instanceof ServerboundUseItemOnPacket useItemOn) {
            InteractionTimingRecorder.instance().recordSend(
                useItemOn.getSequence(),
                System.nanoTime()
            );
        }
    }
}
