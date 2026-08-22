package dev.adrien.crystaloptimizer.v2.debug;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Bounded newest-first-safe diagnostic history; default capacity is deliberately small. */
public final class DecisionTraceBuffer {
    public static final int DEFAULT_CAPACITY = 128;

    private final int capacity;
    private final Deque<DecisionTrace> traces = new ArrayDeque<>();

    public DecisionTraceBuffer() {
        this(DEFAULT_CAPACITY);
    }

    public DecisionTraceBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
    }

    public synchronized void add(DecisionTrace trace) {
        traces.addLast(Objects.requireNonNull(trace, "trace"));
        while (traces.size() > capacity) {
            traces.removeFirst();
        }
    }

    public synchronized List<DecisionTrace> snapshot() {
        return List.copyOf(traces);
    }

    public synchronized void clear() {
        traces.clear();
    }

    public int capacity() {
        return capacity;
    }
}
