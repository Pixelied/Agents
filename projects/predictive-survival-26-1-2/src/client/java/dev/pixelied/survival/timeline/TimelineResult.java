package dev.pixelied.survival.timeline;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;

public record TimelineResult(
    List<TimelineEventResult> eventResults,
    float finalHealth,
    float finalAbsorption,
    boolean survived,
    int consumedDeathProtectionCount,
    Optional<String> firstLethalEventId
) {
    public TimelineResult {
        eventResults = List.copyOf(Objects.requireNonNull(eventResults, "eventResults"));
        firstLethalEventId = Objects.requireNonNull(firstLethalEventId, "firstLethalEventId");
        if (consumedDeathProtectionCount < 0) {
            throw new IllegalArgumentException("consumedDeathProtectionCount must be non-negative");
        }
    }

    public TimelineEventResult eventResult(String id) {
        return eventResults.stream()
            .filter(result -> result.event().id().equals(id))
            .findFirst()
            .orElseThrow(() -> new NoSuchElementException("No event result for id: " + id));
    }
}
