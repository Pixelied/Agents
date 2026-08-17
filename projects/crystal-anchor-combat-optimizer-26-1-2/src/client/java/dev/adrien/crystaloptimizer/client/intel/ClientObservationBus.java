package dev.adrien.crystaloptimizer.client.intel;

import com.mojang.datafixers.util.Pair;
import dev.adrien.crystaloptimizer.intel.OpponentIntelService;
import java.util.Objects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.protocol.game.ClientboundEntityEventPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundTakeItemEntityPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class ClientObservationBus {
    private static final ClientObservationBus INSTANCE = new ClientObservationBus(new OpponentIntelService());

    private final OpponentIntelService intelService;

    ClientObservationBus(OpponentIntelService intelService) {
        this.intelService = Objects.requireNonNull(intelService, "intelService");
    }

    public static ClientObservationBus instance() {
        return INSTANCE;
    }

    public OpponentIntelService intelService() {
        return intelService;
    }

    public void onEquipmentPacket(ClientboundSetEquipmentPacket packet, long timestampNanos) {
        Objects.requireNonNull(packet, "packet");
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        Entity entity = level.getEntity(packet.getEntity());
        if (!(entity instanceof Player player) || isLocalPlayer(player)) {
            return;
        }

        for (Pair<EquipmentSlot, ItemStack> pair : packet.getSlots()) {
            intelService.onVisibleEquipment(
                player.getUUID(),
                pair.getFirst(),
                pair.getSecond(),
                timestampNanos
            );
        }
    }

    public void onPickupPacket(
        ClientboundTakeItemEntityPacket packet,
        ClientLevel level,
        long timestampNanos
    ) {
        Objects.requireNonNull(packet, "packet");
        if (level == null) {
            return;
        }

        Entity itemEntityCandidate = level.getEntity(packet.getItemId());
        Entity collectorCandidate = level.getEntity(packet.getPlayerId());
        if (!(itemEntityCandidate instanceof ItemEntity itemEntity)
            || !(collectorCandidate instanceof Player player)
            || isLocalPlayer(player)) {
            return;
        }

        ItemStack stackBeforeVanillaMutation = itemEntity.getItem().copy();
        if (stackBeforeVanillaMutation.isEmpty() || packet.getAmount() <= 0) {
            return;
        }

        intelService.onPickup(
            player.getUUID(),
            stackBeforeVanillaMutation.getItem(),
            packet.getAmount(),
            timestampNanos
        );
    }

    public void onEntityEventPacket(ClientboundEntityEventPacket packet, long timestampNanos) {
        Objects.requireNonNull(packet, "packet");
        if (packet.getEventId() != EntityEvent.PROTECTED_FROM_DEATH) {
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }

        Entity entity = packet.getEntity(level);
        if (entity instanceof Player player && !isLocalPlayer(player)) {
            intelService.onProtectedFromDeath(player.getUUID(), timestampNanos);
        }
    }

    private static boolean isLocalPlayer(Player player) {
        Player local = Minecraft.getInstance().player;
        return local != null && local.getUUID().equals(player.getUUID());
    }
}
