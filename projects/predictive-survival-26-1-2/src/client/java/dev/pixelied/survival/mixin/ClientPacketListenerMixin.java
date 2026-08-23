package dev.pixelied.survival.mixin;

import dev.pixelied.survival.PredictiveSurvivalClient;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks prediction state dirty only after vanilla has applied relevant entity packet state. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void predictiveSurvival$afterAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
    private void predictiveSurvival$afterSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleEntityPositionSync", at = @At("TAIL"))
    private void predictiveSurvival$afterEntityPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleTeleportEntity", at = @At("TAIL"))
    private void predictiveSurvival$afterTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleMoveEntity", at = @At("TAIL"))
    private void predictiveSurvival$afterMoveEntity(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleRemoveEntities", at = @At("TAIL"))
    private void predictiveSurvival$afterRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty();
    }
}
