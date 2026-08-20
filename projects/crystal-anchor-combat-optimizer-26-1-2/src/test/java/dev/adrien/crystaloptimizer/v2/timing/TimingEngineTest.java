package dev.adrien.crystaloptimizer.v2.timing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class TimingEngineTest {
    @Test
    void placeToSpawnHasItsOwnDistributionAndCountsOneHardBoundary() {
        TimingEngine engine = new TimingEngine(64, 5_000_000_000L);
        long base = 1_000_000_000L;
        for (int i = 0; i < 10; i++) {
            TimingCorrelation key = TimingCorrelation.place(
                TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
                i,
                new BlockPos(i, 64, 0)
            );
            long sent = base + i * 100_000_000L;
            engine.recordStart(key, sent);
            engine.recordEnd(key, sent + (20L + i) * 1_000_000L);
        }

        TimingDistribution distribution = engine.distribution(
            TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
            base + 1_100_000_000L
        );
        assertEquals(10, distribution.sampleCount());
        assertTrue(distribution.p90Millis() >= distribution.p50Millis());
        assertTrue(distribution.confidence() > 0.0);

        SequenceTiming sequence = engine.estimateSequence(
            List.of(TimingTransition.IMMEDIATE, TimingTransition.CRYSTAL_PLACE_TO_SPAWN),
            base + 1_100_000_000L
        );
        assertEquals(1, sequence.hardFeedbackBoundaries());
        assertTrue(sequence.expectedMillis() >= distribution.p50Millis());
    }

    @Test
    void unknownHardFeedbackNeverInventsCompletionTime() {
        TimingEngine engine = new TimingEngine(64, 5_000_000_000L);

        SequenceTiming unknown = engine.estimateSequence(
            List.of(TimingTransition.CRYSTAL_ATTACK_TO_REMOVAL),
            1_000_000_000L
        );

        assertEquals(1, unknown.hardFeedbackBoundaries());
        assertTrue(Double.isInfinite(unknown.expectedMillis()));
        assertEquals(0.0, unknown.confidence());
    }

    @Test
    void immediateTransitionNeedsNoSamplesAndNoFeedbackBoundary() {
        TimingEngine engine = new TimingEngine(64, 5_000_000_000L);

        SequenceTiming immediate = engine.estimateSequence(
            List.of(TimingTransition.IMMEDIATE),
            1_000_000_000L
        );

        assertEquals(0, immediate.hardFeedbackBoundaries());
        assertEquals(0.0, immediate.expectedMillis());
        assertEquals(0.0, immediate.p90Millis());
        assertEquals(1.0, immediate.confidence());
        assertFalse(Double.isInfinite(immediate.expectedMillis()));
    }
}
