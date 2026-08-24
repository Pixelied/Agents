package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;
import java.util.UUID;

public record ReservationToken(UUID id) {
    public ReservationToken {
        Objects.requireNonNull(id, "id");
    }

    public static ReservationToken create() {
        return new ReservationToken(UUID.randomUUID());
    }
}
