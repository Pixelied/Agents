package dev.pixelied.survival.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.PriorityQueue;
import java.util.function.Predicate;

final class BoundedTopKAccumulator<T> {
    private final int cap;
    private final Comparator<? super T> bestFirst;
    private final Predicate<? super T> relevant;
    private final PriorityQueue<Retained<T>> retained;
    private long nextSequence;
    private long totalRelevant;

    BoundedTopKAccumulator(
        int cap,
        Comparator<? super T> bestFirst,
        Predicate<? super T> relevant
    ) {
        if (cap < 0) throw new IllegalArgumentException("cap must not be negative");
        this.cap = cap;
        this.bestFirst = Objects.requireNonNull(bestFirst, "bestFirst");
        this.relevant = Objects.requireNonNull(relevant, "relevant");
        this.retained = new PriorityQueue<>(Math.max(1, cap), this::compareWorstFirst);
    }

    void offer(T value) {
        Objects.requireNonNull(value, "value");
        long sequence = nextSequence++;
        boolean isRelevant = relevant.test(value);
        if (isRelevant) totalRelevant++;
        if (cap == 0) return;

        if (retained.size() < cap) {
            retained.add(new Retained<>(value, sequence, isRelevant));
            return;
        }

        Retained<T> worst = retained.peek();
        int comparison = bestFirst.compare(value, worst.value());
        if (comparison < 0) {
            retained.poll();
            retained.add(new Retained<>(value, sequence, isRelevant));
        }
    }

    int retainedCount() {
        return retained.size();
    }

    Selection<T> finish() {
        List<Retained<T>> ordered = new ArrayList<>(retained);
        ordered.sort(this::compareBestFirstStable);

        List<T> selected = new ArrayList<>(ordered.size());
        long selectedRelevant = 0;
        for (Retained<T> entry : ordered) {
            selected.add(entry.value());
            if (entry.relevant()) selectedRelevant++;
        }
        return new Selection<>(List.copyOf(selected), totalRelevant - selectedRelevant);
    }

    private int compareWorstFirst(Retained<T> left, Retained<T> right) {
        return -compareBestFirstStable(left, right);
    }

    private int compareBestFirstStable(Retained<T> left, Retained<T> right) {
        int valueComparison = bestFirst.compare(left.value(), right.value());
        if (valueComparison != 0) return valueComparison;
        return Long.compare(left.sequence(), right.sequence());
    }

    record Selection<T>(List<T> selected, long omittedRelevant) {
        Selection {
            selected = List.copyOf(selected);
            if (omittedRelevant < 0) throw new IllegalArgumentException("omittedRelevant must not be negative");
        }
    }

    private record Retained<T>(T value, long sequence, boolean relevant) {}
}
