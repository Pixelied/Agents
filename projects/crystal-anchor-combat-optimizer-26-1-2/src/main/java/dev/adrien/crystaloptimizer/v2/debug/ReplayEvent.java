package dev.adrien.crystaloptimizer.v2.debug;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Deterministic relative-time event stored in a replay fixture. */
public record ReplayEvent(long relativeNanos, String type, Map<String, String> fields) {
    public ReplayEvent {
        if (relativeNanos < 0L) {
            throw new IllegalArgumentException("relativeNanos must be non-negative");
        }
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        Objects.requireNonNull(fields, "fields");
        TreeMap<String, String> sorted = new TreeMap<>();
        fields.forEach((key, value) -> sorted.put(
            Objects.requireNonNull(key, "event field key"),
            Objects.requireNonNull(value, "event field value")
        ));
        fields = Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    String canonicalFields() {
        StringBuilder out = new StringBuilder();
        fields.forEach((key, value) -> out.append(key).append('=').append(value).append('\u0000'));
        return out.toString();
    }
}
