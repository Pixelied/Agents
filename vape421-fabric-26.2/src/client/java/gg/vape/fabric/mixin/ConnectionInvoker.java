package gg.vape.fabric.mixin;

import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Connection.class)
public interface ConnectionInvoker {
    @Invoker("channelRead0")
    void vape421$invokeChannelRead0(ChannelHandlerContext context, Packet<?> packet);
}
