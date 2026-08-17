package dev.adrien.crystaloptimizer.intel;

import java.util.Objects;
import java.util.OptionalInt;
import net.minecraft.world.item.Item;

public record ResourceEstimate(
    Item item,
    EvidenceKind kind,
    int lowerBound,
    OptionalInt upperBound,
    double confidence
) {
    public ResourceEstimate {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(upperBound, "upperBound");
        if (lowerBound < 0) {
            throw new IllegalArgumentException("lowerBound must be non-negative");
        }
        if (upperBound.isPresent() && upperBound.getAsInt() < lowerBound) {
            throw new IllegalArgumentException("upperBound must be >= lowerBound");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("confidence must be in [0, 1]");
        }
    }

    public static ResourceEstimate exact(Item item, int count) {
        return new ResourceEstimate(item, EvidenceKind.EXACT, count, OptionalInt.of(count), 1.0);
    }

    public static ResourceEstimate unknown(Item item) {
        return new ResourceEstimate(item, EvidenceKind.ESTIMATED, 0, OptionalInt.empty(), 0.0);
    }
}
