package dev.adrien.crystaloptimizer.v2.execution;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToIntFunction;
import net.minecraft.world.item.Item;

public final class PendingItemLedger {
    private static final long MAX_PENDING_NANOS = 5_000_000_000L;

    private final Map<Long, Reservation> reservations = new LinkedHashMap<>();
    private final Map<Item, Integer> lastObservedCounts = new HashMap<>();

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

        reconcileObservedCount(item, observedCount);
        if (available(item, observedCount) < count) {
            throw new IllegalStateException("insufficient unreserved item count");
        }
        lastObservedCounts.putIfAbsent(item, observedCount);
        reservations.put(actionId, new Reservation(item, count, System.nanoTime()));
    }

    public synchronized void release(long actionId) {
        Reservation released = reservations.remove(actionId);
        if (released != null) {
            cleanupObservation(released.item());
        }
    }

    public synchronized int reconcile(
        ToIntFunction<Item> observedCount,
        long nowNanos
    ) {
        Objects.requireNonNull(observedCount, "observedCount");
        if (reservations.isEmpty()) {
            return 0;
        }

        int releasedReservations = 0;
        Set<Item> items = reservedItemsSnapshot();
        for (Item item : items) {
            int currentCount = Math.max(0, observedCount.applyAsInt(item));
            releasedReservations += reconcileObservedCount(item, currentCount);
        }

        HashSet<Item> touchedItems = new HashSet<>();
        Iterator<Map.Entry<Long, Reservation>> iterator = reservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Reservation reservation = iterator.next().getValue();
            if (nowNanos >= reservation.reservedAtNanos()
                && nowNanos - reservation.reservedAtNanos() >= MAX_PENDING_NANOS) {
                touchedItems.add(reservation.item());
                iterator.remove();
                releasedReservations++;
            }
        }
        for (Item item : touchedItems) {
            cleanupObservation(item);
        }
        return releasedReservations;
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

    private int reconcileObservedCount(Item item, int observedCount) {
        if (reserved(item) == 0) {
            lastObservedCounts.remove(item);
            return 0;
        }

        Integer previous = lastObservedCounts.put(item, observedCount);
        if (previous == null || observedCount >= previous) {
            return 0;
        }

        int remainingConfirmed = previous - observedCount;
        int releasedReservations = 0;
        Iterator<Map.Entry<Long, Reservation>> iterator = reservations.entrySet().iterator();
        while (iterator.hasNext() && remainingConfirmed > 0) {
            Map.Entry<Long, Reservation> entry = iterator.next();
            Reservation reservation = entry.getValue();
            if (reservation.item() != item) {
                continue;
            }

            int confirmed = Math.min(remainingConfirmed, reservation.count());
            remainingConfirmed -= confirmed;
            if (confirmed == reservation.count()) {
                iterator.remove();
                releasedReservations++;
            } else {
                entry.setValue(new Reservation(
                    reservation.item(),
                    reservation.count() - confirmed,
                    reservation.reservedAtNanos()
                ));
            }
        }
        cleanupObservation(item);
        return releasedReservations;
    }

    private Set<Item> reservedItemsSnapshot() {
        HashSet<Item> items = new HashSet<>();
        for (Reservation reservation : reservations.values()) {
            items.add(reservation.item());
        }
        return items;
    }

    private void cleanupObservation(Item item) {
        boolean stillReserved = reservations.values().stream()
            .anyMatch(reservation -> reservation.item() == item);
        if (!stillReserved) {
            lastObservedCounts.remove(item);
        }
    }

    private record Reservation(Item item, int count, long reservedAtNanos) {
    }
}
