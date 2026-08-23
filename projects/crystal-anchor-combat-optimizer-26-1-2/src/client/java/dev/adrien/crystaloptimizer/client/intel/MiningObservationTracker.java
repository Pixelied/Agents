package dev.adrien.crystaloptimizer.client.intel;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;

/**
 * Bounded record of mining facts that are directly observable by the client.
 *
 * <p>The tracker never predicts mining completion. Callers provide an expiry
 * derived from their conservative timing evidence; expiry simply forgets a
 * pending fact and never upgrades it into a successful removal.</p>
 */
public final class MiningObservationTracker {
    private static final int MAX_TRACKED_BLOCKS = 32;
    private final LinkedHashMap<BlockPos, Observation> observations = new LinkedHashMap<>();

    public synchronized void onLocalMiningAction(
        BlockPos pos,
        int sequence,
        long nowNanos,
        long expiresAtNanos
    ) {
        Objects.requireNonNull(pos, "pos");
        if (sequence < 0) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
        requireWindow(nowNanos, expiresAtNanos);
        purgeExpired(nowNanos);
        BlockPos immutable = pos.immutable();
        observations.remove(immutable);
        observations.put(immutable, new Observation(
            immutable,
            sequence,
            nowNanos,
            nowNanos,
            expiresAtNanos,
            false,
            ""
        ));
        trimToBound();
    }

    public synchronized void onBlockAck(int sequence, long nowNanos) {
        if (sequence < 0 || nowNanos < 0L) {
            throw new IllegalArgumentException("sequence and timestamp must be non-negative");
        }
        purgeExpired(nowNanos);
        for (Map.Entry<BlockPos, Observation> entry : observations.entrySet()) {
            Observation current = entry.getValue();
            if (current.sequence() != sequence) {
                continue;
            }
            entry.setValue(new Observation(
                current.pos(),
                current.sequence(),
                current.startedAtNanos(),
                nowNanos,
                current.expiresAtNanos(),
                true,
                current.observedBlockId()
            ));
            return;
        }
    }

    public synchronized void onBlockUpdate(
        BlockPos pos,
        String blockId,
        long nowNanos,
        long expiresAtNanos
    ) {
        Objects.requireNonNull(pos, "pos");
        if (blockId == null || blockId.isBlank()) {
            throw new IllegalArgumentException("blockId must not be blank");
        }
        requireWindow(nowNanos, expiresAtNanos);
        purgeExpired(nowNanos);
        BlockPos immutable = pos.immutable();
        Observation current = observations.get(immutable);
        Observation next = current == null
            ? new Observation(
                immutable,
                -1,
                nowNanos,
                nowNanos,
                expiresAtNanos,
                false,
                blockId
            )
            : new Observation(
                immutable,
                current.sequence(),
                current.startedAtNanos(),
                nowNanos,
                expiresAtNanos,
                current.acknowledged(),
                blockId
            );
        observations.remove(immutable);
        observations.put(immutable, next);
        trimToBound();
    }

    public synchronized Map<BlockPos, Observation> snapshot(long nowNanos) {
        if (nowNanos < 0L) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
        purgeExpired(nowNanos);
        return Map.copyOf(observations);
    }

    public synchronized Set<BlockPos> confirmedAirPositions(long nowNanos) {
        purgeExpiredChecked(nowNanos);
        LinkedHashSet<BlockPos> result = new LinkedHashSet<>();
        observations.forEach((pos, observation) -> {
            if (observation.observedAir()) {
                result.add(pos);
            }
        });
        return Set.copyOf(result);
    }

    public synchronized void clear() {
        observations.clear();
    }

    private void purgeExpiredChecked(long nowNanos) {
        if (nowNanos < 0L) {
            throw new IllegalArgumentException("timestamp must be non-negative");
        }
        purgeExpired(nowNanos);
    }

    private void purgeExpired(long nowNanos) {
        observations.entrySet().removeIf(entry -> nowNanos > entry.getValue().expiresAtNanos());
    }

    private void trimToBound() {
        Iterator<BlockPos> iterator = observations.keySet().iterator();
        while (observations.size() > MAX_TRACKED_BLOCKS && iterator.hasNext()) {
            iterator.next();
            iterator.remove();
        }
    }

    private static void requireWindow(long nowNanos, long expiresAtNanos) {
        if (nowNanos < 0L || expiresAtNanos <= nowNanos) {
            throw new IllegalArgumentException("expiry must be after a non-negative observation timestamp");
        }
    }

    public record Observation(
        BlockPos pos,
        int sequence,
        long startedAtNanos,
        long lastObservedAtNanos,
        long expiresAtNanos,
        boolean acknowledged,
        String observedBlockId
    ) {
        public Observation {
            Objects.requireNonNull(pos, "pos");
            Objects.requireNonNull(observedBlockId, "observedBlockId");
            pos = pos.immutable();
        }

        public boolean observedAir() {
            return "minecraft:air".equals(observedBlockId);
        }
    }
}
