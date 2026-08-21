package dev.adrien.crystaloptimizer.v2.execution;

import dev.adrien.crystaloptimizer.v2.strategy.ResourceChain;
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
        reserveChain(
            actionId,
            ResourceChain.of(Map.of(item, count), 0.0),
            candidate -> candidate == item ? observedCount : 0
        );
    }

    public synchronized void reserveChain(
        long actionId,
        ResourceChain chain,
        ToIntFunction<Item> observedCount
    ) {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(observedCount, "observedCount");
        if (actionId < 0L) {
            throw new IllegalArgumentException("actionId must be non-negative");
        }
        if (reservations.containsKey(actionId)) {
            throw new IllegalStateException("action already has a pending item reservation");
        }
        if (chain.isEmpty()) {
            return;
        }

        LinkedHashMap<Item, Integer> observed = new LinkedHashMap<>();
        for (Map.Entry<Item, Integer> entry : chain.demand().entrySet()) {
            Item item = entry.getKey();
            int current = observedCount.applyAsInt(item);
            if (current < 0) {
                throw new IllegalArgumentException("observedCount must be non-negative");
            }
            reconcileObservedCount(item, current);
            observed.put(item, current);
        }

        for (Map.Entry<Item, Integer> entry : chain.demand().entrySet()) {
            if (available(entry.getKey(), observed.get(entry.getKey())) < entry.getValue()) {
                throw new IllegalStateException("insufficient unreserved item count for resource chain");
            }
        }

        for (Map.Entry<Item, Integer> entry : observed.entrySet()) {
            lastObservedCounts.putIfAbsent(entry.getKey(), entry.getValue());
        }
        reservations.put(
            actionId,
            new Reservation(new LinkedHashMap<>(chain.demand()), System.nanoTime())
        );
    }

    public synchronized void release(long actionId) {
        Reservation released = reservations.remove(actionId);
        if (released != null) {
            for (Item item : released.remaining().keySet()) {
                cleanupObservation(item);
            }
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
                touchedItems.addAll(reservation.remaining().keySet());
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

    public synchronized int availableExcluding(
        long reservationId,
        Item item,
        int observedCount
    ) {
        Objects.requireNonNull(item, "item");
        if (reservationId < 0L) {
            throw new IllegalArgumentException("reservationId must be non-negative");
        }
        if (observedCount < 0) {
            throw new IllegalArgumentException("observedCount must be non-negative");
        }
        int otherReserved = Math.max(0, reserved(item) - reservedBy(reservationId, item));
        return Math.max(0, observedCount - otherReserved);
    }

    public synchronized int reserved(Item item) {
        Objects.requireNonNull(item, "item");
        return reservations.values().stream()
            .mapToInt(reservation -> reservation.remaining().getOrDefault(item, 0))
            .sum();
    }

    public synchronized int reservedBy(long reservationId, Item item) {
        Objects.requireNonNull(item, "item");
        if (reservationId < 0L) {
            throw new IllegalArgumentException("reservationId must be non-negative");
        }
        Reservation reservation = reservations.get(reservationId);
        return reservation == null ? 0 : reservation.remaining().getOrDefault(item, 0);
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
            int reservedForItem = reservation.remaining().getOrDefault(item, 0);
            if (reservedForItem <= 0) {
                continue;
            }

            int confirmed = Math.min(remainingConfirmed, reservedForItem);
            remainingConfirmed -= confirmed;
            LinkedHashMap<Item, Integer> updated = new LinkedHashMap<>(reservation.remaining());
            int left = reservedForItem - confirmed;
            if (left == 0) {
                updated.remove(item);
            } else {
                updated.put(item, left);
            }

            if (updated.isEmpty()) {
                iterator.remove();
                releasedReservations++;
            } else {
                entry.setValue(new Reservation(updated, reservation.reservedAtNanos()));
            }
        }
        cleanupObservation(item);
        return releasedReservations;
    }

    private Set<Item> reservedItemsSnapshot() {
        HashSet<Item> items = new HashSet<>();
        for (Reservation reservation : reservations.values()) {
            items.addAll(reservation.remaining().keySet());
        }
        return items;
    }

    private void cleanupObservation(Item item) {
        boolean stillReserved = reservations.values().stream()
            .anyMatch(reservation -> reservation.remaining().containsKey(item));
        if (!stillReserved) {
            lastObservedCounts.remove(item);
        }
    }

    private record Reservation(Map<Item, Integer> remaining, long reservedAtNanos) {
        private Reservation {
            remaining = Map.copyOf(remaining);
        }
    }
}
