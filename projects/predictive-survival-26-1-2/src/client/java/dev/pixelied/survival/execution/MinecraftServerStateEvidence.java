package dev.pixelied.survival.execution;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundContainerSetContentPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Process-wide client packet evidence. Every mutation is recorded only from a clientbound packet
 * after vanilla has applied that packet, so local interaction prediction cannot manufacture it.
 */
public final class MinecraftServerStateEvidence {
    // Vanilla's direct player-inventory set-slot packet uses container id -2. The cursor-only
    // special id is -1, so keep the exact distinction instead of treating every negative id alike.
    private static final int PLAYER_INVENTORY_CONTAINER_ID = -2;

    private static long revision;
    private static boolean active;
    private static final Map<Integer, ServerStateEvidenceSnapshot.StackEvidence> INVENTORY = new LinkedHashMap<>();
    private static final Map<String, ServerStateEvidenceSnapshot.StackEvidence> EQUIPMENT = new LinkedHashMap<>();
    private static final Map<String, ServerStateEvidenceSnapshot.EffectEvidence> EFFECTS = new LinkedHashMap<>();

    private MinecraftServerStateEvidence() {
    }

    public static synchronized ServerStateEvidenceSnapshot snapshot() {
        return new ServerStateEvidenceSnapshot(active, revision, INVENTORY, EQUIPMENT, EFFECTS);
    }

    public static synchronized void reset() {
        revision = revision == Long.MAX_VALUE ? 0L : revision + 1L;
        active = false;
        INVENTORY.clear();
        EQUIPMENT.clear();
        EFFECTS.clear();
    }

    public static synchronized void observeContainerSetSlot(
        ClientboundContainerSetSlotPacket packet,
        LocalPlayer player
    ) {
        if (packet == null || player == null) return;
        int inventoryIndex = inventoryIndex(packet, player);
        if (inventoryIndex < 0 || inventoryIndex > 40) return;
        active = true;
        long next = nextRevision();
        recordInventory(inventoryIndex, packet.getItem(), next);
    }

    public static synchronized void observeContainerContent(
        ClientboundContainerSetContentPacket packet,
        LocalPlayer player
    ) {
        if (packet == null || player == null || packet.containerId() != player.containerMenu.containerId) return;
        active = true;
        Inventory inventory = player.getInventory();
        long next = nextRevision();
        for (Slot slot : player.containerMenu.slots) {
            if (slot.container != inventory) continue;
            int inventoryIndex = slot.getContainerSlot();
            if (inventoryIndex < 0 || inventoryIndex > 40) continue;
            recordInventory(inventoryIndex, slot.getItem(), next);
        }
    }

    public static synchronized void observeEquipment(
        ClientboundSetEquipmentPacket packet,
        LocalPlayer player
    ) {
        if (packet == null || player == null || packet.getEntity() != player.getId()) return;
        active = true;
        long next = nextRevision();
        for (var pair : packet.getSlots()) {
            EquipmentSlot slot = pair.getFirst();
            ItemStack stack = pair.getSecond();
            EQUIPMENT.put(slot.getName(), stackEvidence(stack, next));
        }
    }

    public static synchronized void observeMobEffect(
        ClientboundUpdateMobEffectPacket packet,
        LocalPlayer player
    ) {
        if (packet == null || player == null || packet.getEntityId() != player.getId()) return;
        active = true;
        long next = nextRevision();
        String key = packet.getEffect().getRegisteredName();
        EFFECTS.put(key, new ServerStateEvidenceSnapshot.EffectEvidence(
            key,
            packet.getEffectAmplifier(),
            packet.getEffectDurationTicks(),
            next
        ));
    }

    private static int inventoryIndex(ClientboundContainerSetSlotPacket packet, LocalPlayer player) {
        if (packet.getContainerId() == PLAYER_INVENTORY_CONTAINER_ID) {
            return packet.getSlot();
        }
        if (packet.getContainerId() != player.containerMenu.containerId) return -1;
        int menuSlot = packet.getSlot();
        if (menuSlot < 0 || menuSlot >= player.containerMenu.slots.size()) return -1;
        Slot slot = player.containerMenu.slots.get(menuSlot);
        if (slot.container != player.getInventory()) return -1;
        return slot.getContainerSlot();
    }

    private static void recordInventory(int inventoryIndex, ItemStack stack, long evidenceRevision) {
        ServerStateEvidenceSnapshot.StackEvidence evidence = stackEvidence(stack, evidenceRevision);
        INVENTORY.put(inventoryIndex, evidence);
        String equipmentSlot = equipmentSlot(inventoryIndex);
        if (equipmentSlot != null) EQUIPMENT.put(equipmentSlot, evidence);
    }

    private static String equipmentSlot(int inventoryIndex) {
        return switch (inventoryIndex) {
            case 36 -> EquipmentSlot.FEET.getName();
            case 37 -> EquipmentSlot.LEGS.getName();
            case 38 -> EquipmentSlot.CHEST.getName();
            case 39 -> EquipmentSlot.HEAD.getName();
            case 40 -> EquipmentSlot.OFFHAND.getName();
            default -> null;
        };
    }

    private static ServerStateEvidenceSnapshot.StackEvidence stackEvidence(ItemStack stack, long evidenceRevision) {
        String key = stack.isEmpty()
            ? "minecraft:air"
            : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        return new ServerStateEvidenceSnapshot.StackEvidence(
            key,
            stack.isEmpty() ? 0 : ItemStack.hashItemAndComponents(stack),
            stack.getCount(),
            evidenceRevision
        );
    }

    private static long nextRevision() {
        if (revision == Long.MAX_VALUE) {
            revision = 1L;
            INVENTORY.clear();
            EQUIPMENT.clear();
            EFFECTS.clear();
            return revision;
        }
        return ++revision;
    }
}
