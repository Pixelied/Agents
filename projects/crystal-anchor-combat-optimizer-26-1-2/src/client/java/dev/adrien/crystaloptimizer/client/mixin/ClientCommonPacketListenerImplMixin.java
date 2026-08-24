package dev.adrien.crystaloptimizer.client.mixin;

import dev.adrien.crystaloptimizer.client.execution.InteractionTimingRecorder;
import dev.adrien.crystaloptimizer.client.v2.ClientTimingObserver;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class ClientCommonPacketListenerImplMixin {
    @Inject(method = "send", at = @At("HEAD"))
    private void crystaloptimizer$recordCombatTiming(Packet<?> packet, CallbackInfo ci) {
        long nowNanos = System.nanoTime();
        if (packet instanceof ServerboundUseItemOnPacket useItemOn) {
            InteractionTimingRecorder.instance().recordSend(
                useItemOn.getSequence(),
                nowNanos
            );
            ClientTimingObserver.instance().onBlockInteractionSent(
                useItemOn.getSequence(),
                nowNanos
            );

            var player = Minecraft.getInstance().player;
            if (player != null
                && player.getItemInHand(useItemOn.getHand()).is(Items.END_CRYSTAL)) {
                ClientTimingObserver.instance().onCrystalPlaceSent(
                    useItemOn.getSequence(),
                    useItemOn.getHitResult().getBlockPos(),
                    nowNanos
                );
            }
            return;
        }

        if (packet instanceof ServerboundAttackPacket attack) {
            var level = Minecraft.getInstance().level;
            if (level != null && level.getEntity(attack.entityId()) instanceof EndCrystal) {
                ClientTimingObserver.instance().onCrystalAttackSent(
                    attack.entityId(),
                    nowNanos
                );
            }
        }
    }
}
