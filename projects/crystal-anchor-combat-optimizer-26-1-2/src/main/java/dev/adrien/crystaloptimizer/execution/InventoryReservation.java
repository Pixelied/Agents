package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;

public record InventoryReservation(ReservationToken token, ReservationRequest request) {
    public InventoryReservation {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(request, "request");
    }
}
