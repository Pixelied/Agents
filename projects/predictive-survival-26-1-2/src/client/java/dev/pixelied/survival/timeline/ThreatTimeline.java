package dev.pixelied.survival.timeline;

import java.util.List;
import java.util.Objects;

public record ThreatTimeline(List<ThreatEvent> events) {
    public ThreatTimeline {
        events = List.copyOf(Objects.requireNonNull(events, "events"));
    }
}
