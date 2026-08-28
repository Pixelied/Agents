package dev.pixelied.survival.timeline;

import dev.pixelied.survival.core.TickWindow;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable threat timeline plus explicit world-source identities and consequences of event occurrence.
 * Player damage acceptance is intentionally separate from world-event occurrence.
 * Spawned threat impact windows are relative to the event that creates them.
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

        LinkedHashMap<String, ThreatEvent> initialEvents = initialEvents(timeline);
        if (!sourceIdsByEventId.keySet().equals(initialEvents.keySet())) {
            throw new IllegalArgumentException("every initial causal threat event must have exactly one source id");
        }
        validateSourceIds(sourceIdsByEventId);

        LinkedHashMap<String, ThreatEvent> declaredEvents = new LinkedHashMap<>(initialEvents);
        LinkedHashMap<String, String> allSources = new LinkedHashMap<>(sourceIdsByEventId);
        Set<String> spawnedSourceIds = new LinkedHashSet<>();
        for (Map.Entry<String, List<ThreatTransition>> entry : transitionsByEventId.entrySet()) {
            for (ThreatTransition transition : entry.getValue()) {
                if (!(transition instanceof ThreatTransition.SpawnThreat spawn)) continue;
                ThreatEvent previous = declaredEvents.putIfAbsent(spawn.event().id(), spawn.event());
                if (previous != null) {
                    throw new IllegalArgumentException("duplicate causal threat event id: " + spawn.event().id());
                }
                if (sourceIdsByEventId.containsValue(spawn.sourceId()) || !spawnedSourceIds.add(spawn.sourceId())) {
                    throw new IllegalArgumentException("spawned source id must identify one new source: " + spawn.sourceId());
                }
                allSources.put(spawn.event().id(), spawn.sourceId());
            }
        }
        if (!declaredEvents.keySet().containsAll(transitionsByEventId.keySet())) {
            throw new IllegalArgumentException("transitions must be keyed by a declared causal event");
        }
        validateSourceIds(allSources);
    }

    public ThreatTimeline expandedTimeline() {
        LinkedHashMap<String, ThreatEvent> declared = declaredEvents();
        LinkedHashMap<String, ThreatEvent> expanded = new LinkedHashMap<>();
        Deque<ThreatEvent> queue = new ArrayDeque<>();
        for (ThreatEvent event : timeline.events()) {
            expanded.put(event.id(), event);
            queue.addLast(event);
        }

        while (!queue.isEmpty()) {
            ThreatEvent trigger = queue.removeFirst();
            for (ThreatTransition transition : transitionsAfter(trigger.id())) {
                if (!(transition instanceof ThreatTransition.SpawnThreat spawn)) continue;
                ThreatEvent absolute = shiftFromTrigger(trigger, spawn.event());
                if (expanded.putIfAbsent(absolute.id(), absolute) != null) {
                    throw new IllegalArgumentException("spawned event reached more than once: " + absolute.id());
                }
                queue.addLast(absolute);
            }
        }
        if (!expanded.keySet().equals(declared.keySet())) {
            Set<String> unreachable = new LinkedHashSet<>(declared.keySet());
            unreachable.removeAll(expanded.keySet());
            throw new IllegalArgumentException("unreachable spawned causal events: " + unreachable);
        }
        return new ThreatTimeline(new ArrayList<>(expanded.values()));
    }

    public String sourceId(ThreatEvent event) {
        Objects.requireNonNull(event, "event");
        String sourceId = allSourceIdsByEventId().get(event.id());
        if (sourceId == null) throw new IllegalArgumentException("unknown causal event: " + event.id());
        return sourceId;
    }

    public Set<String> initialSourceIds() {
        return Set.copyOf(sourceIdsByEventId.values());
    }

    public Optional<String> spawnTriggerEventId(String sourceId) {
        Objects.requireNonNull(sourceId, "sourceId");
        String trigger = null;
        for (Map.Entry<String, List<ThreatTransition>> entry : transitionsByEventId.entrySet()) {
            for (ThreatTransition transition : entry.getValue()) {
                if (!(transition instanceof ThreatTransition.SpawnThreat spawn)
                    || !spawn.sourceId().equals(sourceId)) continue;
                if (trigger != null && !trigger.equals(entry.getKey())) {
                    throw new IllegalStateException("spawned source has multiple trigger events: " + sourceId);
                }
                trigger = entry.getKey();
            }
        }
        return Optional.ofNullable(trigger);
    }

    public List<ThreatTransition> transitionsAfter(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        return transitionsByEventId.getOrDefault(eventId, List.of());
    }

    private LinkedHashMap<String, ThreatEvent> declaredEvents() {
        LinkedHashMap<String, ThreatEvent> declared = initialEvents(timeline);
        for (List<ThreatTransition> transitions : transitionsByEventId.values()) {
            for (ThreatTransition transition : transitions) {
                if (transition instanceof ThreatTransition.SpawnThreat spawn) {
                    declared.put(spawn.event().id(), spawn.event());
                }
            }
        }
        return declared;
    }

    private Map<String, String> allSourceIdsByEventId() {
        LinkedHashMap<String, String> all = new LinkedHashMap<>(sourceIdsByEventId);
        for (List<ThreatTransition> transitions : transitionsByEventId.values()) {
            for (ThreatTransition transition : transitions) {
                if (transition instanceof ThreatTransition.SpawnThreat spawn) {
                    all.put(spawn.event().id(), spawn.sourceId());
                }
            }
        }
        return Map.copyOf(all);
    }

    private static LinkedHashMap<String, ThreatEvent> initialEvents(ThreatTimeline timeline) {
        LinkedHashMap<String, ThreatEvent> events = new LinkedHashMap<>();
        for (ThreatEvent event : timeline.events()) {
            if (events.putIfAbsent(event.id(), event) != null) {
                throw new IllegalArgumentException("causal timeline requires unique event ids");
            }
        }
        return events;
    }

    private static ThreatEvent shiftFromTrigger(ThreatEvent trigger, ThreatEvent relative) {
        TickWindow shifted = new TickWindow(
            saturatingAdd(trigger.impact().earliest(), relative.impact().earliest()),
            saturatingAdd(trigger.impact().latest(), relative.impact().latest())
        );
        return new ThreatEvent(
            relative.id(), relative.kind(), shifted, relative.damage(), relative.confidence(),
            relative.sourcePosition(), relative.impactPosition(), relative.avoidable(), relative.blockable(),
            relative.relocatable(), relative.canDisableBlocking(), relative.requiresAcceptedEventId()
        );
    }

    private static long saturatingAdd(long value, long increment) {
        if (increment > 0L && value > Long.MAX_VALUE - increment) return Long.MAX_VALUE;
        if (increment < 0L && value < Long.MIN_VALUE - increment) return Long.MIN_VALUE;
        return value + increment;
    }

    private static void validateSourceIds(Map<String, String> sources) {
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            String sourceId = entry.getValue();
            if (sourceId == null || sourceId.isBlank()) {
                throw new IllegalArgumentException("source id must not be blank for event " + entry.getKey());
            }
        }
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
