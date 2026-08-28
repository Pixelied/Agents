package dev.pixelied.survival.timeline;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Immutable threat timeline plus explicit world-source identities and consequences of event occurrence.
 * Player damage acceptance is intentionally separate from world-event occurrence.
 */
public record CausalThreatTimeline(
    ThreatTimeline timeline,
    Map<String, String> sourceIdsByEventId,
    Map<String, List<ThreatTransition>> transitionsByEventId
) {
    public CausalThreatTimeline {
        timeline = Objects.requireNonNull(timeline, "timeline");
        sourceIdsByEventId = Map.copyOf(Objects.requireNonNull(sourceIdsByEventId, "sourceIdsByEventId"));
        transitionsByEventId = copyTransitions(transitionsByEventId);

        Set<String> eventIds = timeline.events().stream()
            .map(ThreatEvent::id)
            .collect(Collectors.toUnmodifiableSet());
        if (eventIds.size() != timeline.events().size()) {
            throw new IllegalArgumentException("causal timeline requires unique event ids");
        }
        if (!sourceIdsByEventId.keySet().equals(eventIds)) {
            throw new IllegalArgumentException("every causal threat event must have exactly one source id");
        }
        for (Map.Entry<String, String> entry : sourceIdsByEventId.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                throw new IllegalArgumentException("source id must not be blank for event " + entry.getKey());
            }
        }
        if (!eventIds.containsAll(transitionsByEventId.keySet())) {
            throw new IllegalArgumentException("transitions must be keyed by an event in the timeline");
        }
    }

    public String sourceId(ThreatEvent event) {
        Objects.requireNonNull(event, "event");
        String sourceId = sourceIdsByEventId.get(event.id());
        if (sourceId == null) throw new IllegalArgumentException("unknown causal event: " + event.id());
        return sourceId;
    }

    public List<ThreatTransition> transitionsAfter(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return transitionsByEventId.getOrDefault(eventId, List.of());
    }

    private static Map<String, List<ThreatTransition>> copyTransitions(
        Map<String, List<ThreatTransition>> transitions
    ) {
        Objects.requireNonNull(transitions, "transitionsByEventId");
        LinkedHashMap<String, List<ThreatTransition>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<ThreatTransition>> entry : transitions.entrySet()) {
            String eventId = Objects.requireNonNull(entry.getKey(), "transition event id");
            if (eventId.isBlank()) throw new IllegalArgumentException("transition event id must not be blank");
            List<ThreatTransition> values = Objects.requireNonNull(entry.getValue(), "transitions for " + eventId);
            copy.put(eventId, values.stream().map(value -> Objects.requireNonNull(value, "transition")).toList());
        }
        return Map.copyOf(copy);
    }
}
