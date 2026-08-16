package dev.adrien.spearclient.mixin;

import dev.adrien.spearclient.network.ServerStateTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(
        method = "handleMovePlayer",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V",
            shift = At.Shift.AFTER
        )
    )
    private void spearclient$observeCorrection(
        ClientboundPlayerPositionPacket packet,
        CallbackInfo ci
    ) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        PositionMoveRotation current = PositionMoveRotation.of(player);
        PositionMoveRotation corrected = PositionMoveRotation.calculateAbsolute(
            current,
            packet.change(),
            packet.relatives()
        );
        ServerStateTracker.shared().onCorrection(corrected.position());
    }
}
