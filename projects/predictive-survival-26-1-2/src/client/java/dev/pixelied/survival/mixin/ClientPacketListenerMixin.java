package dev.pixelied.survival.mixin;

import dev.pixelied.survival.PredictiveSurvivalClient;
import dev.pixelied.survival.core.LocalDamageObservationBuffer;
import dev.pixelied.survival.execution.DeathProtectionPopTracker;
import dev.pixelied.survival.execution.MinecraftServerStateEvidence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks prediction state dirty only after vanilla has applied relevant server packet state. */
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
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && packet.id() == minecraft.player.getId()) {
            LocalDamageObservationBuffer.invalidate();
        }
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void predictiveSurvival$afterMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        // This is the authoritative local-player teleport/correction path in 26.1.2.
        LocalDamageObservationBuffer.invalidate();
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

    @Inject(method = "handleSetHealth", at = @At("TAIL"))
    private void predictiveSurvival$afterSetHealth(ClientboundSetHealthPacket packet, CallbackInfo ci) {
        LocalDamageObservationBuffer.observeHealth(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void predictiveSurvival$afterDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        LocalDamageObservationBuffer.observeDamageEvent(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    private void predictiveSurvival$beforeSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        LocalDamageObservationBuffer.beforeEntityData(packet, Minecraft.getInstance().player);
    }

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void predictiveSurvival$afterSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        if (LocalDamageObservationBuffer.afterEntityData(packet, Minecraft.getInstance().player)) {
            PredictiveSurvivalClient.markThreatDirty();
        }
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void predictiveSurvival$beforeRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        LocalDamageObservationBuffer.invalidate();
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void predictiveSurvival$afterEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (packet.getEventId() != 35 || minecraft.level == null || minecraft.player == null) return;
        if (packet.getEntity(minecraft.level) != minecraft.player) return;
        DeathProtectionPopTracker.global().observeLocalTotemPop(Math.max(0L, minecraft.player.tickCount));
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void predictiveSurvival$afterBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void predictiveSurvival$afterChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void predictiveSurvival$afterContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        MinecraftServerStateEvidence.observeContainerSetSlot(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void predictiveSurvival$afterContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        MinecraftServerStateEvidence.observeContainerContent(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleSetEquipment", at = @At("TAIL"))
    private void predictiveSurvival$afterSetEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        MinecraftServerStateEvidence.observeEquipment(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty();
    }

    @Inject(method = "handleUpdateMobEffect", at = @At("TAIL"))
    private void predictiveSurvival$afterUpdateMobEffect(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
        MinecraftServerStateEvidence.observeMobEffect(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty();
    }
}
