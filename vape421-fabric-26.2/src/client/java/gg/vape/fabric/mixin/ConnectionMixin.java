package gg.vape.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import gg.vape.fabric.network.RecoveredPacketAdapter;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Connection.class)
abstract class ConnectionMixin {
    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketSendListener;Z)V")
    private void vape421$send(
            Packet<?> packet,
            @Nullable PacketSendListener listener,
            boolean flush,
            Operation<Void> original) {
        RecoveredPacketAdapter.Result result =
                RecoveredPacketAdapter.outbound(this, packet);
        if (!result.cancelled()) {
            original.call((Packet<?>) result.packet(), listener, flush);
        }
    }

    @WrapMethod(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V")
    private void vape421$channelRead0(
            ChannelHandlerContext context,
            Packet<?> packet,
            Operation<Void> original) {
        RecoveredPacketAdapter.Result result =
                RecoveredPacketAdapter.inbound(this, packet);
        if (!result.cancelled()) {
            original.call(context, (Packet<?>) result.packet());
        }
    }
}
