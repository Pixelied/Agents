package dev.adrien.crystaloptimizer.intel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class OpponentIntelService {
    private final Map<UUID, MutableIntel> states = new HashMap<>();

    public synchronized void onVisibleEquipment(
        UUID opponentId,
        EquipmentSlot slot,
        ItemStack stack,
        long timestampNanos
    ) {
        Objects.requireNonNull(opponentId, "opponentId");
        Objects.requireNonNull(slot, "slot");
        Objects.requireNonNull(stack, "stack");
        requireTimestamp(timestampNanos);

        MutableIntel state = state(opponentId);
        Item item = stack.getItem();
        int count = stack.getCount();
        ResourceEstimate exact = ResourceEstimate.exact(item, count);
        state.visibleEquipment.put(slot, exact);
        state.observations.add(new OpponentObservation(
            opponentId,
            OpponentObservation.Type.VISIBLE_EQUIPMENT,
            EvidenceKind.EXACT,
            timestampNanos,
            java.util.Optional.of(slot),
            java.util.Optional.of(item),
            count
        ));

        if ((slot == EquipmentSlot.MAINHAND || slot == EquipmentSlot.OFFHAND)
            && item == Items.TOTEM_OF_UNDYING
            && count > 0
            && state.pendingProtectedFromDeathAt >= 0L
            && timestampNanos >= state.pendingProtectedFromDeathAt) {
            long latency = timestampNanos - state.pendingProtectedFromDeathAt;
            state.refillLatencyNanos.add(latency);
            state.observations.add(new OpponentObservation(
                opponentId,
                OpponentObservation.Type.OBSERVED_REFILL,
                EvidenceKind.DERIVED,
                timestampNanos,
                java.util.Optional.of(slot),
                java.util.Optional.of(item),
                count
            ));
            state.pendingProtectedFromDeathAt = -1L;
        }
    }

    public synchronized void onPickup(
        UUID opponentId,
        Item item,
        int amount,
        long timestampNanos
    ) {
        Objects.requireNonNull(opponentId, "opponentId");
        Objects.requireNonNull(item, "item");
        requireTimestamp(timestampNanos);
        if (amount <= 0) {
            throw new IllegalArgumentException("pickup amount must be positive");
        }

        MutableIntel state = state(opponentId);
        state.observedPickups.merge(item, amount, Integer::sum);
        state.observations.add(new OpponentObservation(
            opponentId,
            OpponentObservation.Type.PICKUP,
            EvidenceKind.EXACT,
            timestampNanos,
            java.util.Optional.empty(),
            java.util.Optional.of(item),
            amount
        ));
    }

    public synchronized void onPickup(
        UUID opponentId,
        ItemStack stack,
        int amount,
        long timestampNanos
    ) {
        Objects.requireNonNull(stack, "stack");
        onPickup(opponentId, stack.getItem(), amount, timestampNanos);
    }

    public synchronized void onProtectedFromDeath(UUID opponentId, long timestampNanos) {
        Objects.requireNonNull(opponentId, "opponentId");
        requireTimestamp(timestampNanos);
        MutableIntel state = state(opponentId);
        state.confirmedPops++;
        state.pendingProtectedFromDeathAt = timestampNanos;
        state.observations.add(new OpponentObservation(
            opponentId,
            OpponentObservation.Type.PROTECTED_FROM_DEATH,
            EvidenceKind.EXACT,
            timestampNanos,
            java.util.Optional.empty(),
            java.util.Optional.empty(),
            1
        ));
    }

    public synchronized OpponentIntel snapshot(UUID opponentId) {
        Objects.requireNonNull(opponentId, "opponentId");
        MutableIntel state = state(opponentId);
        ResourceEstimate mainhand = state.visibleEquipment.getOrDefault(
            EquipmentSlot.MAINHAND,
            ResourceEstimate.unknown(Items.AIR)
        );
        ResourceEstimate offhand = state.visibleEquipment.getOrDefault(
            EquipmentSlot.OFFHAND,
            ResourceEstimate.unknown(Items.AIR)
        );
        return new OpponentIntel(
            opponentId,
            mainhand,
            offhand,
            state.visibleEquipment,
            state.observedPickups,
            state.confirmedPops,
            ResourceEstimate.unknown(Items.TOTEM_OF_UNDYING),
            state.observations,
            new OpponentResponseProfile(state.refillLatencyNanos)
        );
    }

    public void updateEquipment(UUID opponentId, EquipmentSlot slot, ItemStack stack, long timestampNanos) {
        onVisibleEquipment(opponentId, slot, stack, timestampNanos);
    }

    public void recordPickup(UUID opponentId, ItemStack stack, int amount, long timestampNanos) {
        onPickup(opponentId, stack, amount, timestampNanos);
    }

    public void recordTotemPop(UUID opponentId, long timestampNanos) {
        onProtectedFromDeath(opponentId, timestampNanos);
    }

    private MutableIntel state(UUID opponentId) {
        return states.computeIfAbsent(opponentId, ignored -> new MutableIntel());
    }

    private static void requireTimestamp(long timestampNanos) {
        if (timestampNanos < 0L) {
            throw new IllegalArgumentException("timestampNanos must be non-negative");
        }
    }

    private static final class MutableIntel {
        private final EnumMap<EquipmentSlot, ResourceEstimate> visibleEquipment = new EnumMap<>(EquipmentSlot.class);
        private final Map<Item, Integer> observedPickups = new HashMap<>();
        private final List<OpponentObservation> observations = new ArrayList<>();
        private final List<Long> refillLatencyNanos = new ArrayList<>();
        private int confirmedPops;
        private long pendingProtectedFromDeathAt = -1L;
    }
}
