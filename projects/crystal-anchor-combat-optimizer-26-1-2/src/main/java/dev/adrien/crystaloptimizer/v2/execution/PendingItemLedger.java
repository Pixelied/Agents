package dev.adrien.crystaloptimizer.v2.execution;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;
import net.minecraft.world.item.Item;

public final class PendingItemLedger {
    private static final long MAX_PENDING_NANOS = 5_000_000_000L;

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
        reservations.put(
            actionId,
            new Reservation(item, count, observedCount, System.nanoTime())
        );
    }

    public synchronized void release(long actionId) {
        reservations.remove(actionId);
    }

    public synchronized int reconcile(
        ToIntFunction<Item> observedCount,
        long nowNanos
    ) {
        Objects.requireNonNull(observedCount, "observedCount");
        int released = 0;
        Iterator<Map.Entry<Long, Reservation>> iterator = reservations.entrySet().iterator();
        while (iterator.hasNext()) {
            Reservation reservation = iterator.next().getValue();
            int currentCount = Math.max(0, observedCount.applyAsInt(reservation.item()));
            int expectedAfterSpend = Math.max(
                0,
                reservation.observedCountAtReserve() - reservation.count()
            );
            boolean consumptionVisible = currentCount <= expectedAfterSpend;
            boolean timedOut = nowNanos >= reservation.reservedAtNanos()
                && nowNanos - reservation.reservedAtNanos() >= MAX_PENDING_NANOS;
            if (consumptionVisible || timedOut) {
                iterator.remove();
                released++;
            }
        }
        return released;
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

    private record Reservation(
        Item item,
        int count,
        int observedCountAtReserve,
        long reservedAtNanos
    ) {
    }
}
