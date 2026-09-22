package gg.vape.fabric.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.Operation;
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
    @WrapMethod(method = "sendPacket")
    private void vape421$sendPacket(Packet<?> packet, @Nullable ChannelFutureListener callback, boolean flush, Operation<Void> original) {
        RecoveredPacketAdapter.Result result=RecoveredPacketAdapter.outbound(this,packet);
        if(!result.cancelled()) original.call((Packet<?>)result.packet(),callback,flush);
    }
    @WrapMethod(method = "channelRead0")
    private void vape421$channelRead0(ChannelHandlerContext context, Packet<?> packet, Operation<Void> original) {
        RecoveredPacketAdapter.Result result=RecoveredPacketAdapter.inbound(this,packet);
        if(!result.cancelled()) original.call(context,(Packet<?>)result.packet());
    }
}
