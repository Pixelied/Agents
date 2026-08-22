package dev.adrien.crystaloptimizer.v2.debug;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Complete immutable deterministic input for one replayed strategic decision. */
public record ReplayFixture(
    StrategicSnapshot snapshot,
    OptimizerConfig config,
    List<ReplayEvent> events
) {
    private static final Comparator<ReplayEvent> EVENT_ORDER = Comparator
        .comparingLong(ReplayEvent::relativeNanos)
        .thenComparing(ReplayEvent::type)
        .thenComparing(ReplayEvent::canonicalFields);

    public ReplayFixture {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(events, "events");
        events = events.stream()
            .map(event -> Objects.requireNonNull(event, "replay event"))
            .sorted(EVENT_ORDER)
            .toList();
    }
}
