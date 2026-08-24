package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.v2.timing.TimingCorrelation;
import dev.adrien.crystaloptimizer.v2.timing.TimingEngine;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class ClientTimingObserver {
    private static final ClientTimingObserver INSTANCE = new ClientTimingObserver(
        new TimingEngine(64, 5_000_000_000L)
    );

    private final TimingEngine timingEngine;
    private final Map<BlockPos, ArrayDeque<TimingCorrelation>> pendingPlacements = new HashMap<>();

    ClientTimingObserver(TimingEngine timingEngine) {
        this.timingEngine = Objects.requireNonNull(timingEngine, "timingEngine");
    }

    public static ClientTimingObserver instance() {
        return INSTANCE;
    }

    public TimingEngine timingEngine() {
        return timingEngine;
    }

    public void onBlockInteractionSent(int sequence, long nowNanos) {
        timingEngine.recordStart(
            TimingCorrelation.sequence(TimingTransition.BLOCK_INTERACTION_TO_ACK, sequence),
            nowNanos
        );
    }

    public void onBlockAck(int sequence, long nowNanos) {
        timingEngine.recordEnd(
            TimingCorrelation.sequence(TimingTransition.BLOCK_INTERACTION_TO_ACK, sequence),
            nowNanos
        );
    }

    public synchronized void onCrystalPlaceSent(
        int sequence,
        BlockPos basePos,
        long nowNanos
    ) {
        BlockPos base = Objects.requireNonNull(basePos, "basePos").immutable();
        TimingCorrelation correlation = TimingCorrelation.place(
            TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
            sequence,
            base
        );
        timingEngine.recordStart(correlation, nowNanos);
        pendingPlacements
            .computeIfAbsent(base, ignored -> new ArrayDeque<>())
            .addLast(correlation);
    }

    public synchronized void onCrystalSpawned(BlockPos basePos, long nowNanos) {
        BlockPos base = Objects.requireNonNull(basePos, "basePos").immutable();
        ArrayDeque<TimingCorrelation> queue = pendingPlacements.get(base);
        if (queue == null || queue.isEmpty()) {
            return;
        }
        TimingCorrelation correlation = queue.removeFirst();
        if (queue.isEmpty()) {
            pendingPlacements.remove(base);
        }
        timingEngine.recordEnd(correlation, nowNanos);
    }

    public void onCrystalAttackSent(int entityId, long nowNanos) {
        timingEngine.recordStart(
            TimingCorrelation.entity(TimingTransition.CRYSTAL_ATTACK_TO_REMOVAL, entityId),
            nowNanos
        );
    }

    public void onCrystalRemoved(int entityId, long nowNanos) {
        timingEngine.recordEnd(
            TimingCorrelation.entity(TimingTransition.CRYSTAL_ATTACK_TO_REMOVAL, entityId),
            nowNanos
        );
    }

    public void onTotemPopped(UUID targetId, long nowNanos) {
        timingEngine.recordStart(
            TimingCorrelation.player(TimingTransition.TOTEM_POP_TO_VISIBLE_REFILL, targetId),
            nowNanos
        );
    }

    public void onVisibleTotem(UUID targetId, long nowNanos) {
        timingEngine.recordEnd(
            TimingCorrelation.player(TimingTransition.TOTEM_POP_TO_VISIBLE_REFILL, targetId),
            nowNanos
        );
    }
}
