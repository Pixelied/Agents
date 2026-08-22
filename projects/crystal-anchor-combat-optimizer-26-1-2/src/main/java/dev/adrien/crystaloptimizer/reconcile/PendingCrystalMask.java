package dev.adrien.crystaloptimizer.reconcile;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Reversible local suppression for crystals whose attack was actually sent.
 *
 * <p>This never mutates the client world or removes entities. It only tells bot
 * logic that an observed crystal is expected to disappear until the server
 * confirms removal or the conservative timeout expires.</p>
 */
public final class PendingCrystalMask {
    private static final int MAX_PENDING = 64;
    private final LinkedHashMap<Integer, Pending> pending = new LinkedHashMap<>();

    public synchronized void markAttacked(int entityId, BlockPos basePos, long expiresAtNanos) {
        if (entityId <= 0) {
            throw new IllegalArgumentException("entityId must be positive");
        }
        Objects.requireNonNull(basePos, "basePos");
        if (expiresAtNanos < 0L) {
            throw new IllegalArgumentException("expiresAtNanos must be non-negative");
        }
        pending.remove(entityId);
        pending.put(entityId, new Pending(entityId, basePos.immutable(), expiresAtNanos));
        trimToBound();
    }

    public synchronized boolean isPendingRemoval(
        int entityId,
        BlockPos basePos,
        long nowNanos
    ) {
        Objects.requireNonNull(basePos, "basePos");
        purgeExpired(nowNanos);
        Pending entry = pending.get(entityId);
        return entry != null && entry.basePos().equals(basePos);
    }

    public synchronized boolean isPendingRemoval(int entityId, long nowNanos) {
        purgeExpired(nowNanos);
        return pending.containsKey(entityId);
    }

    public synchronized Optional<Removal> confirmRemoved(int entityId) {
        Pending removed = pending.remove(entityId);
        return removed == null
            ? Optional.empty()
            : Optional.of(new Removal(removed.entityId(), removed.basePos()));
    }

    public synchronized Set<Removal> reconcile(Set<Integer> observedCrystalIds, long nowNanos) {
        Objects.requireNonNull(observedCrystalIds, "observedCrystalIds");
        purgeExpired(nowNanos);
        LinkedHashSet<Removal> removed = new LinkedHashSet<>();
        Iterator<Map.Entry<Integer, Pending>> iterator = pending.entrySet().iterator();
        while (iterator.hasNext()) {
            Pending entry = iterator.next().getValue();
            if (observedCrystalIds.contains(entry.entityId())) {
                continue;
            }
            removed.add(new Removal(entry.entityId(), entry.basePos()));
            iterator.remove();
        }
        return Set.copyOf(removed);
    }

    public synchronized void clear() {
        pending.clear();
    }

    private void purgeExpired(long nowNanos) {
        if (nowNanos < 0L) {
            throw new IllegalArgumentException("nowNanos must be non-negative");
        }
        pending.entrySet().removeIf(entry -> nowNanos > entry.getValue().expiresAtNanos());
    }

    private void trimToBound() {
        Iterator<Integer> iterator = pending.keySet().iterator();
        while (pending.size() > MAX_PENDING && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private record Pending(int entityId, BlockPos basePos, long expiresAtNanos) {
    }

    public record Removal(int entityId, BlockPos basePos) {
        public Removal {
            if (entityId <= 0) {
                throw new IllegalArgumentException("entityId must be positive");
            }
            Objects.requireNonNull(basePos, "basePos");
            basePos = basePos.immutable();
        }
    }
}
