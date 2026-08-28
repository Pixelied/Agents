package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundedTopKAccumulatorTest {
    private static final Comparator<Entry> BEST_FIRST = Comparator
        .comparingInt(Entry::threatPriority)
        .thenComparingInt(Entry::distanceBucket);

    @Test
    void retainsOnlyTheConfiguredTopKWhileMatchingStableFullSort() {
        int cap = 17;
        BoundedTopKAccumulator<Entry> accumulator = new BoundedTopKAccumulator<>(
            cap,
            BEST_FIRST,
            Entry::threatRelevant
        );
        List<Entry> offered = new ArrayList<>();

        for (int i = 0; i < 10_000; i++) {
            Entry entry = new Entry(
                "entry-" + i,
                i % 5 == 0 ? 0 : 1,
                (i * 37) % 23,
                i % 5 == 0
            );
            offered.add(entry);
            accumulator.offer(entry);
            assertTrue(
                accumulator.retainedCount() <= cap,
                "broad-phase selector retained more than its configured cap"
            );
        }

        BoundedTopKAccumulator.Selection<Entry> selection = accumulator.finish();
        List<Entry> expected = offered.stream().sorted(BEST_FIRST).limit(cap).toList();
        long expectedOmittedRelevant = offered.stream().filter(Entry::threatRelevant).count()
            - expected.stream().filter(Entry::threatRelevant).count();

        assertEquals(expected, selection.selected());
        assertEquals(expectedOmittedRelevant, selection.omittedRelevant());
        assertEquals(cap, accumulator.retainedCount());
    }

    @Test
    void equalRankEntriesKeepEncounterOrderLikeThePreviousStableSort() {
        BoundedTopKAccumulator<Entry> accumulator = new BoundedTopKAccumulator<>(
            3,
            BEST_FIRST,
            Entry::threatRelevant
        );
        Entry first = new Entry("first", 0, 4, true);
        Entry second = new Entry("second", 0, 4, true);
        Entry third = new Entry("third", 0, 4, true);
        Entry fourth = new Entry("fourth", 0, 4, true);

        accumulator.offer(first);
        accumulator.offer(second);
        accumulator.offer(third);
        accumulator.offer(fourth);

        assertEquals(List.of(first, second, third), accumulator.finish().selected());
    }

    private record Entry(String id, int threatPriority, int distanceBucket, boolean threatRelevant) {}
}
