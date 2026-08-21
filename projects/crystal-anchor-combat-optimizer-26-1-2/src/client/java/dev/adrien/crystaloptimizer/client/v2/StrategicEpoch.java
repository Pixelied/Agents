package dev.adrien.crystaloptimizer.client.v2;

import dev.adrien.crystaloptimizer.v2.strategy.DamageMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class StrategicEpoch {
    @FunctionalInterface
    public interface DamageMapFactory {
        DamageMap build(UUID targetId);
    }

    private final DamageMapFactory factory;
    private final Map<UUID, DamageMap> maps = new LinkedHashMap<>();
    private final Map<UUID, Integer> buildCounts = new LinkedHashMap<>();

    public StrategicEpoch(DamageMapFactory factory) {
        this.factory = Objects.requireNonNull(factory, "factory");
    }

    public DamageMap damageMap(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        DamageMap cached = maps.get(targetId);
        if (cached != null) {
            return cached;
        }
        DamageMap built = Objects.requireNonNull(
            factory.build(targetId),
            "damage map for " + targetId
        );
        maps.put(targetId, built);
        buildCounts.merge(targetId, 1, Integer::sum);
        return built;
    }

    public int buildCount(UUID targetId) {
        Objects.requireNonNull(targetId, "targetId");
        return buildCounts.getOrDefault(targetId, 0);
    }

    public Map<UUID, Integer> buildCounts() {
        return Map.copyOf(buildCounts);
    }
}
