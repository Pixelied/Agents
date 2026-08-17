package dev.adrien.crystaloptimizer.client.mixin;

import dev.adrien.crystaloptimizer.client.intel.ClientObservationBus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleSetEquipment", at = @At("TAIL"))
    private void crystaloptimizer$equipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        ClientObservationBus.instance().onEquipmentPacket(packet, System.nanoTime());
    }

    @Inject(method = "handleTakeItemEntity", at = @At("HEAD"))
    private void crystaloptimizer$pickup(ClientboundTakeItemEntityPacket packet, CallbackInfo ci) {
        ClientObservationBus.instance().onPickupPacket(
            packet,
            Minecraft.getInstance().level,
            System.nanoTime()
        );
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void crystaloptimizer$entityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        ClientObservationBus.instance().onEntityEventPacket(packet, System.nanoTime());
    }
}
