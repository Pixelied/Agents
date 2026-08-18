package dev.pixelied.survival.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record FallingBlockDamageSnapshot(
    boolean hurtEntities,
    int fallDamageMax,
    float fallDamagePerDistance,
    double fallDistance,
    String damageSource
) {
    public FallingBlockDamageSnapshot {
        if (fallDamageMax < 0) throw new IllegalArgumentException("fallDamageMax must be non-negative");
        if (!Float.isFinite(fallDamagePerDistance) || fallDamagePerDistance < 0f) {
            throw new IllegalArgumentException("fallDamagePerDistance must be finite and non-negative");
        }
        if (!Double.isFinite(fallDistance) || fallDistance < 0d) {
            throw new IllegalArgumentException("fallDistance must be finite and non-negative");
        }
        damageSource = Objects.requireNonNull(damageSource, "damageSource");
        if (damageSource.isBlank()) throw new IllegalArgumentException("damageSource must not be blank");
    }

    public Map<String, String> properties() {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("hurt_entities", Boolean.toString(hurtEntities));
        properties.put("fall_damage_max", Integer.toString(fallDamageMax));
        properties.put("fall_damage_per_distance", Float.toString(fallDamagePerDistance));
        properties.put("fall_distance", Double.toString(fallDistance));
        properties.put("damage_source", damageSource);
        return Map.copyOf(properties);
    }
}
