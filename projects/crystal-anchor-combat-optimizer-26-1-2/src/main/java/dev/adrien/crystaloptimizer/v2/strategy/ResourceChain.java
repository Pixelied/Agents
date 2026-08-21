package dev.adrien.crystaloptimizer.v2.strategy;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.item.Item;

public record ResourceChain(Map<Item, Integer> demand, double cost) {
    private static final ResourceChain NONE = new ResourceChain(Map.of(), 0.0);

    public ResourceChain {
        Objects.requireNonNull(demand, "demand");
        if (!Double.isFinite(cost) || cost < 0.0) {
            throw new IllegalArgumentException("cost must be finite and non-negative");
        }

        LinkedHashMap<Item, Integer> copy = new LinkedHashMap<>();
        demand.forEach((item, count) -> {
            Objects.requireNonNull(item, "resource item");
            if (count == null || count <= 0) {
                throw new IllegalArgumentException("resource counts must be positive");
            }
            copy.put(item, count);
        });
        demand = Map.copyOf(copy);
    }

    public static ResourceChain none() {
        return NONE;
    }

    public static ResourceChain of(Map<Item, Integer> demand, double cost) {
        if (demand.isEmpty() && cost == 0.0) {
            return NONE;
        }
        return new ResourceChain(demand, cost);
    }

    public int count(Item item) {
        return demand.getOrDefault(Objects.requireNonNull(item, "item"), 0);
    }

    public boolean isEmpty() {
        return demand.isEmpty();
    }
}
