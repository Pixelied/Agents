package dev.pixelied.survival.mixin;

import dev.pixelied.survival.PredictiveSurvivalClient;
import dev.pixelied.survival.core.LocalDamageObservationBuffer;
import dev.pixelied.survival.core.SurvivalStateInvalidationReason;
import dev.pixelied.survival.execution.DeathProtectionPopTracker;
import dev.pixelied.survival.execution.MinecraftServerStateEvidence;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundChangeDifficultyPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundInitializeBorderPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundRespawnPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderCenterPacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderLerpSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetBorderSizePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Marks prediction state dirty only after vanilla has applied relevant server packet state. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void predictiveSurvival$afterAddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.ENTITY_ADDED);
    }

    @Inject(method = "handleSetEntityMotion", at = @At("TAIL"))
    private void predictiveSurvival$afterSetEntityMotion(ClientboundSetEntityMotionPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.ENTITY_MOTION);
    }

    @Inject(method = "handleEntityPositionSync", at = @At("TAIL"))
    private void predictiveSurvival$afterEntityPositionSync(ClientboundEntityPositionSyncPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.ENTITY_POSITION);
    }

    @Inject(method = "handleTeleportEntity", at = @At("TAIL"))
    private void predictiveSurvival$afterTeleportEntity(ClientboundTeleportEntityPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markRemoteEntityDiscontinuity(packet.id());
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && packet.id() == minecraft.player.getId()) {
            LocalDamageObservationBuffer.invalidate();
        }
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.ENTITY_POSITION);
    }

    @Inject(method = "handleMovePlayer", at = @At("TAIL"))
    private void predictiveSurvival$afterMovePlayer(ClientboundPlayerPositionPacket packet, CallbackInfo ci) {
        // This is the authoritative local-player teleport/correction path in 26.1.2.
        LocalDamageObservationBuffer.invalidate();
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.LOCAL_PLAYER_CORRECTION);
    }

    @Inject(method = "handleMoveEntity", at = @At("TAIL"))
    private void predictiveSurvival$afterMoveEntity(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.ENTITY_POSITION);
    }

    @Inject(method = "handleRemoveEntities", at = @At("TAIL"))
    private void predictiveSurvival$afterRemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.ENTITY_REMOVED);
    }

    @Inject(method = "handleSetHealth", at = @At("TAIL"))
    private void predictiveSurvival$afterSetHealth(ClientboundSetHealthPacket packet, CallbackInfo ci) {
        LocalDamageObservationBuffer.observeHealth(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.LOCAL_HEALTH);
    }

    @Inject(method = "handleDamageEvent", at = @At("TAIL"))
    private void predictiveSurvival$afterDamageEvent(ClientboundDamageEventPacket packet, CallbackInfo ci) {
        LocalDamageObservationBuffer.observeDamageEvent(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.LOCAL_DAMAGE_EVENT);
    }

    @Inject(method = "handleSetEntityData", at = @At("HEAD"))
    private void predictiveSurvival$beforeSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        LocalDamageObservationBuffer.beforeEntityData(packet, Minecraft.getInstance().player);
    }

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void predictiveSurvival$afterSetEntityData(ClientboundSetEntityDataPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean localAbsorptionChanged = LocalDamageObservationBuffer.afterEntityData(packet, minecraft.player);
        if (localAbsorptionChanged || predictiveSurvival$isThreatRelevantEntity(packet.id(), minecraft)) {
            PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.RELEVANT_ENTITY_METADATA);
        }
    }

    @Inject(method = "handleRespawn", at = @At("HEAD"))
    private void predictiveSurvival$beforeRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        LocalDamageObservationBuffer.invalidate();
    }

    @Inject(method = "handleRespawn", at = @At("TAIL"))
    private void predictiveSurvival$afterRespawn(ClientboundRespawnPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.RESPAWN_RESET);
    }

    @Inject(method = "handleEntityEvent", at = @At("TAIL"))
    private void predictiveSurvival$afterEntityEvent(ClientboundEntityEventPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) return;
        Entity entity = packet.getEntity(minecraft.level);
        if (entity == null) return;

        if (packet.getEventId() == 35 && entity == minecraft.player) {
            DeathProtectionPopTracker.global().observeLocalTotemPop(Math.max(0L, minecraft.player.tickCount));
            PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.LOCAL_TOTEM_POP);
            return;
        }
        if (packet.getEventId() == 10 && entity instanceof MinecartTNT) {
            PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.TNT_MINECART_PRIMED);
        }
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void predictiveSurvival$afterBlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.BLOCK_UPDATE);
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void predictiveSurvival$afterChunkBlocksUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.BLOCK_UPDATE);
    }

    @Inject(method = "handleContainerSetSlot", at = @At("TAIL"))
    private void predictiveSurvival$afterContainerSetSlot(ClientboundContainerSetSlotPacket packet, CallbackInfo ci) {
        MinecraftServerStateEvidence.observeContainerSetSlot(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.INVENTORY_SLOT);
    }

    @Inject(method = "handleContainerContent", at = @At("TAIL"))
    private void predictiveSurvival$afterContainerContent(ClientboundContainerSetContentPacket packet, CallbackInfo ci) {
        MinecraftServerStateEvidence.observeContainerContent(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.INVENTORY_CONTENT);
    }

    @Inject(method = "handleSetEquipment", at = @At("TAIL"))
    private void predictiveSurvival$afterSetEquipment(ClientboundSetEquipmentPacket packet, CallbackInfo ci) {
        MinecraftServerStateEvidence.observeEquipment(packet, Minecraft.getInstance().player);
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.EQUIPMENT);
    }

    @Inject(method = "handleUpdateMobEffect", at = @At("TAIL"))
    private void predictiveSurvival$afterUpdateMobEffect(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        MinecraftServerStateEvidence.observeMobEffect(packet, minecraft.player);
        if (predictiveSurvival$isThreatRelevantEntity(packet.getEntityId(), minecraft)) {
            PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.EFFECT_UPDATED);
        }
    }

    @Inject(method = "handleRemoveMobEffect", at = @At("TAIL"))
    private void predictiveSurvival$afterRemoveMobEffect(ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (predictiveSurvival$isThreatRelevantEntity(packet.entityId(), minecraft)) {
            PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.EFFECT_REMOVED);
        }
    }

    @Inject(method = "handleUpdateAttributes", at = @At("TAIL"))
    private void predictiveSurvival$afterUpdateAttributes(ClientboundUpdateAttributesPacket packet, CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!predictiveSurvival$isThreatRelevantEntity(packet.getEntityId(), minecraft)) return;
        boolean relevant = packet.getValues().stream().anyMatch(value ->
            SurvivalStateInvalidationReason.isSurvivalRelevantAttribute(value.attribute().getRegisteredName())
        );
        if (relevant) {
            PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.ATTRIBUTE_UPDATE);
        }
    }

    @Inject(method = "handleInitializeBorder", at = @At("TAIL"))
    private void predictiveSurvival$afterInitializeBorder(ClientboundInitializeBorderPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.WORLD_BORDER);
    }

    @Inject(method = "handleSetBorderCenter", at = @At("TAIL"))
    private void predictiveSurvival$afterSetBorderCenter(ClientboundSetBorderCenterPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.WORLD_BORDER);
    }

    @Inject(method = "handleSetBorderSize", at = @At("TAIL"))
    private void predictiveSurvival$afterSetBorderSize(ClientboundSetBorderSizePacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.WORLD_BORDER);
    }

    @Inject(method = "handleSetBorderLerpSize", at = @At("TAIL"))
    private void predictiveSurvival$afterSetBorderLerpSize(ClientboundSetBorderLerpSizePacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.WORLD_BORDER);
    }

    @Inject(method = "handleChangeDifficulty", at = @At("TAIL"))
    private void predictiveSurvival$afterChangeDifficulty(ClientboundChangeDifficultyPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.DIFFICULTY);
    }

    @Inject(method = "handlePlayerAbilities", at = @At("TAIL"))
    private void predictiveSurvival$afterPlayerAbilities(ClientboundPlayerAbilitiesPacket packet, CallbackInfo ci) {
        PredictiveSurvivalClient.markThreatDirty(SurvivalStateInvalidationReason.PLAYER_ABILITIES);
    }

    private static boolean predictiveSurvival$isThreatRelevantEntity(int entityId, Minecraft minecraft) {
        if (minecraft == null || minecraft.level == null) return false;
        Entity entity = minecraft.level.getEntity(entityId);
        if (entity == null) return false;
        return entity == minecraft.player
            || entity instanceof Player
            || entity instanceof Enemy
            || entity instanceof Goat;
    }
}
