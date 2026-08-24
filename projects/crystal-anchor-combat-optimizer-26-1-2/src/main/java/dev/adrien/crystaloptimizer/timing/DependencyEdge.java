package dev.adrien.crystaloptimizer.timing;

import java.util.Objects;

public record DependencyEdge(String fromAction, String toAction, PacketDependency dependency) {
    public DependencyEdge {
        if (fromAction == null || fromAction.isBlank()) {
            throw new IllegalArgumentException("fromAction must not be blank");
        }
        if (toAction == null || toAction.isBlank()) {
            throw new IllegalArgumentException("toAction must not be blank");
        }
        Objects.requireNonNull(dependency, "dependency");
    }
}
