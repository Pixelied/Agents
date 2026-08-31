package dev.pixelied.survival.timing;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bounded per-runtime history for remote kinematic observations. The tracker records at most one
 * sample per logical client tick and carries the timing model's conservative inbound observation-age
 * window alongside that history. It does not apply entity-specific physics; projectile/melee
 * consumers can replay the bounded observations with their own audited motion model.
 */
public final class RemoteEntityKinematicEnvelope {
    private final int historyLimit;
    private final int trackedEntityLimit;
    private final LinkedHashMap<String, Track> tracks = new LinkedHashMap<>(16, 0.75f, true);

    public RemoteEntityKinematicEnvelope(int historyLimit, int trackedEntityLimit) {
        if (historyLimit <= 0) throw new IllegalArgumentException("historyLimit must be positive");
        if (trackedEntityLimit <= 0) throw new IllegalArgumentException("trackedEntityLimit must be positive");
        this.historyLimit = historyLimit;
        this.trackedEntityLimit = trackedEntityLimit;
    }

    public Snapshot observe(
        String entityId,
        String incarnationKey,
        long logicalTick,
        Vec3Snapshot position,
        Vec3Snapshot velocity,
        TimingSnapshot timing,
        boolean discontinuity
    ) {
        entityId = requireText(entityId, "entityId");
        incarnationKey = requireText(incarnationKey, "incarnationKey");
        if (logicalTick < 0L) throw new IllegalArgumentException("logicalTick must be non-negative");
        position = Objects.requireNonNull(position, "position");
        velocity = Objects.requireNonNull(velocity, "velocity");
        timing = Objects.requireNonNull(timing, "timing");

        Track track = tracks.get(entityId);
        boolean resetBoundary = track == null
            || discontinuity
            || !track.incarnationKey.equals(incarnationKey)
            || logicalTick < track.lastLogicalTick();
        if (resetBoundary) {
            track = new Track(incarnationKey);
            tracks.put(entityId, track);
            trimTrackedEntities();
        }

        Sample sample = new Sample(logicalTick, position, velocity);
        if (!track.history.isEmpty() && track.history.getLast().logicalTick() == logicalTick) {
            track.history.removeLast();
        }
        track.history.addLast(sample);
        while (track.history.size() > historyLimit) track.history.removeFirst();

        return new Snapshot(
            entityId,
            incarnationKey,
            timing.observationAgeWindow(),
            List.copyOf(track.history),
            resetBoundary
        );
    }

    public int trackedEntityCount() {
        return tracks.size();
    }

    public void reset() {
        tracks.clear();
    }

    private void trimTrackedEntities() {
        while (tracks.size() > trackedEntityLimit) {
            Iterator<Map.Entry<String, Track>> iterator = tracks.entrySet().iterator();
            if (!iterator.hasNext()) return;
            iterator.next();
            iterator.remove();
        }
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must be nonblank");
        return value;
    }

    private static final class Track {
        private final String incarnationKey;
        private final ArrayDeque<Sample> history = new ArrayDeque<>();

        private Track(String incarnationKey) {
            this.incarnationKey = incarnationKey;
        }

        private long lastLogicalTick() {
            return history.isEmpty() ? -1L : history.getLast().logicalTick();
        }
    }

    public record Sample(long logicalTick, Vec3Snapshot position, Vec3Snapshot velocity) {
        public Sample {
            if (logicalTick < 0L) throw new IllegalArgumentException("logicalTick must be non-negative");
            position = Objects.requireNonNull(position, "position");
            velocity = Objects.requireNonNull(velocity, "velocity");
        }
    }

    public record Snapshot(
        String entityId,
        String incarnationKey,
        TickWindow observationAgeTicks,
        List<Sample> history,
        boolean resetBoundary
    ) {
        public Snapshot {
            entityId = requireText(entityId, "entityId");
            incarnationKey = requireText(incarnationKey, "incarnationKey");
            observationAgeTicks = Objects.requireNonNull(observationAgeTicks, "observationAgeTicks");
            history = List.copyOf(Objects.requireNonNull(history, "history"));
            if (history.isEmpty()) throw new IllegalArgumentException("history must not be empty");
        }

        public Sample latest() {
            return history.getLast();
        }
    }
}
