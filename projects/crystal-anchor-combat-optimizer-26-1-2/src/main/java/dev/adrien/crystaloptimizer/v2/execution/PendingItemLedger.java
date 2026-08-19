package dev.adrien.crystaloptimizer.v2.execution;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.item.Item;

public final class PendingItemLedger {
    private final Map<Long, Reservation> reservations = new HashMap<>();

    public synchronized void reserve(
        long actionId,
        Item item,
        int count,
        int observedCount
    ) {
        Objects.requireNonNull(item, "item");
        if (actionId < 0L) {
            throw new IllegalArgumentException("actionId must be non-negative");
        }
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        if (observedCount < 0) {
            throw new IllegalArgumentException("observedCount must be non-negative");
        }
        if (reservations.containsKey(actionId)) {
            throw new IllegalStateException("action already has a pending item reservation");
        }
        if (available(item, observedCount) < count) {
            throw new IllegalStateException("insufficient unreserved item count");
        }
        reservations.put(actionId, new Reservation(item, count));
    }

    public synchronized void release(long actionId) {
        reservations.remove(actionId);
    }

    public synchronized int available(Item item, int observedCount) {
        Objects.requireNonNull(item, "item");
        if (observedCount < 0) {
            throw new IllegalArgumentException("observedCount must be non-negative");
        }
        return Math.max(0, observedCount - reserved(item));
    }

    public synchronized int reserved(Item item) {
        Objects.requireNonNull(item, "item");
        return reservations.values().stream()
            .filter(reservation -> reservation.item() == item)
            .mapToInt(Reservation::count)
            .sum();
    }

    public synchronized boolean hasReservation(long actionId) {
        return reservations.containsKey(actionId);
    }

    public synchronized int reservationCount() {
        return reservations.size();
    }

    private record Reservation(Item item, int count) {
    }
}
