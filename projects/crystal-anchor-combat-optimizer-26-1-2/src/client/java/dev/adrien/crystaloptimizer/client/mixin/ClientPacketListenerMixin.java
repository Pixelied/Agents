package dev.adrien.crystaloptimizer.client.mixin;

import dev.adrien.crystaloptimizer.client.execution.InteractionTimingRecorder;
import dev.adrien.crystaloptimizer.client.intel.ClientObservationBus;
import dev.adrien.crystaloptimizer.client.intel.RemoteDamageWindowObserver;
import dev.adrien.crystaloptimizer.client.intel.TargetMotionTracker;
import dev.adrien.crystaloptimizer.client.v2.ClientCombatEventBus;
import dev.adrien.crystaloptimizer.client.v2.ClientRevisionTracker;
import dev.adrien.crystaloptimizer.client.v2.ClientTimingObserver;
import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundBlockChangedAckPacket;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundEntityPositionSyncPacket;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
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

    @Inject(method = "handleSetEntityData", at = @At("TAIL"))
    private void crystaloptimizer$remoteDamageWindow(
        ClientboundSetEntityDataPacket packet,
        CallbackInfo ci
    ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(packet.id());
        if (!(entity instanceof AbstractClientPlayer player)) {
            return;
        }
        RemoteDamageWindowObserver.instance().onObservedTargetState(
            player.getUUID(),
            player.getHealth(),
            player.invulnerableTime,
            System.nanoTime()
        );
    }

    @Inject(method = "handleMoveEntity", at = @At("TAIL"))
    private void crystaloptimizer$targetMoveEntity(ClientboundMoveEntityPacket packet, CallbackInfo ci) {
        if (!packet.hasPosition()) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        crystaloptimizer$publishTargetMovement(packet.getEntity(level), false);
    }

    @Inject(method = "handleEntityPositionSync", at = @At("TAIL"))
    private void crystaloptimizer$targetPositionSync(
        ClientboundEntityPositionSyncPacket packet,
        CallbackInfo ci
    ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        crystaloptimizer$publishTargetMovement(level.getEntity(packet.id()), false);
    }

    @Inject(method = "handleTeleportEntity", at = @At("TAIL"))
    private void crystaloptimizer$targetTeleport(
        ClientboundTeleportEntityPacket packet,
        CallbackInfo ci
    ) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        crystaloptimizer$publishTargetMovement(level.getEntity(packet.id()), true);
    }

    @Inject(method = "handleAddEntity", at = @At("TAIL"))
    private void crystaloptimizer$v2AddEntity(ClientboundAddEntityPacket packet, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        Entity entity = level.getEntity(packet.getId());
        if (!(entity instanceof EndCrystal crystal)) {
            return;
        }

        BlockPos base = BlockPos.containing(
            crystal.getX(),
            crystal.getY() - 1.0,
            crystal.getZ()
        ).immutable();
        long nowNanos = System.nanoTime();
        ClientTimingObserver.instance().onCrystalSpawned(base, nowNanos);
        ClientCombatEventBus.instance().publish(
            new CombatEvent.CrystalSpawned(crystal.getId(), base, nowNanos)
        );
    }

    @Inject(method = "handleRemoveEntities", at = @At("HEAD"))
    private void crystaloptimizer$v2RemoveEntities(ClientboundRemoveEntitiesPacket packet, CallbackInfo ci) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        for (int entityId : packet.getEntityIds()) {
            Entity entity = level.getEntity(entityId);
            if (entity instanceof AbstractClientPlayer player) {
                TargetMotionTracker.instance().remove(player.getUUID());
            }
            if (!(entity instanceof EndCrystal crystal)) {
                continue;
            }
            BlockPos base = BlockPos.containing(
                crystal.getX(),
                crystal.getY() - 1.0,
                crystal.getZ()
            ).immutable();
            long nowNanos = System.nanoTime();
            ClientTimingObserver.instance().onCrystalRemoved(entityId, nowNanos);
            ClientCombatEventBus.instance().publish(
                new CombatEvent.CrystalRemoved(entityId, base, nowNanos)
            );
        }
    }

    @Inject(method = "handleBlockUpdate", at = @At("TAIL"))
    private void crystaloptimizer$v2BlockUpdate(ClientboundBlockUpdatePacket packet, CallbackInfo ci) {
        long nowNanos = System.nanoTime();
        ClientCombatEventBus.instance().publish(
            new CombatEvent.BlockChanged(packet.getPos().immutable(), nowNanos)
        );
    }

    @Inject(method = "handleChunkBlocksUpdate", at = @At("TAIL"))
    private void crystaloptimizer$v2ChunkBlocksUpdate(
        ClientboundSectionBlocksUpdatePacket packet,
        CallbackInfo ci
    ) {
        long nowNanos = System.nanoTime();
        packet.runUpdates((pos, state) -> ClientCombatEventBus.instance().publish(
            new CombatEvent.BlockChanged(pos.immutable(), nowNanos)
        ));
    }

    @Inject(method = "handleBlockChangedAck", at = @At("TAIL"))
    private void crystaloptimizer$blockChangedAck(ClientboundBlockChangedAckPacket packet, CallbackInfo ci) {
        long nowNanos = System.nanoTime();
        InteractionTimingRecorder.instance().recordAck(packet.sequence(), nowNanos);
        ClientTimingObserver.instance().onBlockAck(packet.sequence(), nowNanos);
        ClientCombatEventBus.instance().publish(
            new CombatEvent.BlockAcked(packet.sequence(), nowNanos)
        );
    }

    private static void crystaloptimizer$publishTargetMovement(Entity entity, boolean correction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(entity instanceof AbstractClientPlayer player) || player == minecraft.player) {
            return;
        }
        long nowNanos = System.nanoTime();
        TargetMotionTracker.instance().observe(
            player.getUUID(),
            player.position(),
            player.getBoundingBox(),
            player.getDeltaMovement(),
            correction,
            nowNanos
        );
        long revision = ClientRevisionTracker.instance().markTargetMovement(player.getUUID());
        ClientCombatEventBus.instance().publish(
            new CombatEvent.TargetMoved(player.getUUID(), revision, nowNanos)
        );
    }
}
