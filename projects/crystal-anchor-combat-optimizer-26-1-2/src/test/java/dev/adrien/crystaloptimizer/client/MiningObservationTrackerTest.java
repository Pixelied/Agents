package dev.adrien.crystaloptimizer.client;

import dev.adrien.crystaloptimizer.client.intel.MiningObservationTracker;
import java.util.Map;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class MiningObservationTrackerTest {
    @Test
    void localMiningAckAndObservedAirRemainBoundedObservableFacts() {
        MiningObservationTracker tracker = new MiningObservationTracker();
        BlockPos pos = new BlockPos(3, 64, 4);
        long now = 1_000L;
        long expires = 10_000L;

        tracker.onLocalMiningAction(pos, 17, now, expires);
        tracker.onBlockAck(17, now + 10L);
        tracker.onBlockUpdate(pos, "minecraft:air", now + 20L, expires);

        MiningObservationTracker.Observation observation = tracker.snapshot(now + 30L).get(pos);
        assertTrue(observation.acknowledged());
        assertEquals("minecraft:air", observation.observedBlockId());
        assertTrue(observation.observedAir());
    }

    @Test
    void expiredPendingMiningFactsAreDroppedInsteadOfInventingCompletion() {
        MiningObservationTracker tracker = new MiningObservationTracker();
        BlockPos pos = new BlockPos(1, 70, 1);
        tracker.onLocalMiningAction(pos, 3, 100L, 200L);

        Map<BlockPos, MiningObservationTracker.Observation> expired = tracker.snapshot(201L);

        assertFalse(expired.containsKey(pos));
        assertTrue(tracker.confirmedAirPositions(201L).isEmpty());
    }
}
