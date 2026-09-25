package gg.vape.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import gg.vape.fabric.network.RecoveredPacketAdapter;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Connection.class)
abstract class ConnectionMixin {
    /*
     * In 26.2 the three-argument send method is the common outbound path.
     * Wrapping the real call, rather than merely observing it, preserves
     * Vape's packet cancellation and packet replacement semantics.
     */
    @WrapMethod(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V")
    private void vape421$send(
            Packet<?> packet,
            @Nullable ChannelFutureListener callback,
            boolean flush,
            Operation<Void> original) {
        RecoveredPacketAdapter.Result result =
                RecoveredPacketAdapter.outbound((Connection) (Object) this, packet);
        if (!result.cancelled()) {
            original.call((Packet<?>) result.packet(), callback, flush);
        }
    }

    /*
     * Every inbound packet crosses Connection#channelRead0 before vanilla
     * dispatch. Replacing the argument passed to original.call mirrors the old
     * Javassist task's "$2 = event.getPacketInstance()" behavior.
     */
    @WrapMethod(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/protocol/Packet;)V")
    private void vape421$channelRead0(
            ChannelHandlerContext context,
            Packet<?> packet,
            Operation<Void> original) {
        RecoveredPacketAdapter.Result result =
                RecoveredPacketAdapter.inbound((Connection) (Object) this, packet);
        if (!result.cancelled()) {
            original.call(context, (Packet<?>) result.packet());
        }
    }
}
