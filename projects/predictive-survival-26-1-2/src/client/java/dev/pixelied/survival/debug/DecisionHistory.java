package dev.pixelied.survival.debug;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

public final class DecisionHistory {
    private final int capacity;
    private final Deque<DecisionRecord> records = new ArrayDeque<>();

    public DecisionHistory(int capacity) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        this.capacity = capacity;
    }

    public synchronized void add(DecisionRecord record) {
        records.addLast(Objects.requireNonNull(record, "record"));
        while (records.size() > capacity) records.removeFirst();
    }

    public synchronized List<DecisionRecord> snapshot() {
        return List.copyOf(new ArrayList<>(records));
    }

    public int capacity() {
        return capacity;
    }
}
