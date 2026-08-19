package dev.adrien.crystaloptimizer.v2.timing;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimingReplayTest {
    @Test
    void replayedDistributionsPreservePercentilesAndExposeJitter() throws Exception {
        Replay low = replay("low-ping.trace");
        Replay jitter = replay("jitter.trace");
        Replay degraded = replay("degraded-tps.trace");

        for (Replay replay : new Replay[] { low, jitter, degraded }) {
            for (TimingTransition transition : replay.seen().keySet()) {
                TimingDistribution distribution = replay.engine().distribution(transition, replay.nowNanos());
                assertTrue(distribution.p90Millis() >= distribution.p50Millis());
            }
        }

        assertTrue(
            jitter.engine().distribution(TimingTransition.CRYSTAL_PLACE_TO_SPAWN, jitter.nowNanos()).p90Millis()
                > low.engine().distribution(TimingTransition.CRYSTAL_PLACE_TO_SPAWN, low.nowNanos()).p90Millis()
        );
        assertTrue(
            degraded.engine().distribution(TimingTransition.BLOCK_INTERACTION_TO_ACK, degraded.nowNanos()).p90Millis()
                > low.engine().distribution(TimingTransition.BLOCK_INTERACTION_TO_ACK, low.nowNanos()).p90Millis()
        );
    }

    @Test
    void staleSamplesDecayAndMissingHardFeedbackIsUnknown() throws Exception {
        Replay low = replay("low-ping.trace");
        TimingDistribution fresh = low.engine().distribution(
            TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
            low.nowNanos()
        );
        TimingDistribution stale = low.engine().distribution(
            TimingTransition.CRYSTAL_PLACE_TO_SPAWN,
            low.nowNanos() + 6_000_000_000L
        );
        assertTrue(fresh.confidence() > stale.confidence());
        assertEquals(0.0, stale.confidence(), 1.0e-9);

        Replay degraded = replay("degraded-tps.trace");
        SequenceTiming missing = degraded.engine().estimateSequence(
            java.util.List.of(TimingTransition.TOTEM_POP_TO_VISIBLE_REFILL),
            degraded.nowNanos()
        );
        assertEquals(0.0, missing.confidence(), 1.0e-9);
        assertEquals(1, missing.hardFeedbackBoundaries());
    }

    private static Replay replay(String file) throws Exception {
        String resource = "/dev/adrien/crystaloptimizer/v2/timing/" + file;
        InputStream input = TimingReplayTest.class.getResourceAsStream(resource);
        assertNotNull(input, "missing timing replay resource " + resource);
        TimingEngine engine = new TimingEngine(64, 5_000_000_000L);
        Map<TimingTransition, Integer> seen = new EnumMap<>(TimingTransition.class);
        long newest = 0L;

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("transition,")) {
                    continue;
                }
                String[] parts = line.split(",");
                TimingTransition transition = TimingTransition.valueOf(parts[0]);
                long start = Long.parseLong(parts[1]);
                long end = Long.parseLong(parts[2]);
                long high = Long.parseLong(parts[3]);
                long low = Long.parseLong(parts[4]);
                TimingCorrelation correlation = new TimingCorrelation(transition, high, low);
                engine.recordStart(correlation, start);
                assertTrue(engine.recordEnd(correlation, end));
                seen.merge(transition, 1, Integer::sum);
                newest = Math.max(newest, end);
            }
        }
        return new Replay(engine, seen, newest + 100_000_000L);
    }

    private record Replay(
        TimingEngine engine,
        Map<TimingTransition, Integer> seen,
        long nowNanos
    ) {}
}
