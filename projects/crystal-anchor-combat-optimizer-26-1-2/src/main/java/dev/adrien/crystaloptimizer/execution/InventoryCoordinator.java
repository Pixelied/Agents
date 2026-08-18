package dev.adrien.crystaloptimizer.execution;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class InventoryCoordinator {
    private final Map<ReservationToken, InventoryReservation> active = new HashMap<>();
    private final List<Consumer<ReservationRequest>> listeners = new ArrayList<>();

    public synchronized ReservationResult reserve(ReservationRequest request) {
        Objects.requireNonNull(request, "request");
        List<InventoryReservation> conflicts = active.values().stream()
            .filter(reservation -> conflicts(reservation.request(), request))
            .toList();

        boolean blocked = conflicts.stream()
            .anyMatch(reservation -> reservation.request().priority() >= request.priority());
        if (blocked) {
            return ReservationResult.denied("conflicting reservation has equal or higher priority");
        }

        List<ReservationToken> revoked = new ArrayList<>();
        for (InventoryReservation conflict : conflicts) {
            active.remove(conflict.token());
            revoked.add(conflict.token());
        }

        ReservationToken token = ReservationToken.create();
        active.put(token, new InventoryReservation(token, request));
        for (Consumer<ReservationRequest> listener : List.copyOf(listeners)) {
            listener.accept(request);
        }
        return ReservationResult.granted(token, revoked);
    }

    public synchronized void release(ReservationToken token) {
        if (token != null) {
            active.remove(token);
        }
    }

    public synchronized boolean isActive(ReservationToken token) {
        return token != null && active.containsKey(token);
    }

    public synchronized List<InventoryReservation> activeReservations() {
        return List.copyOf(active.values());
    }

    public synchronized void addReservationListener(Consumer<ReservationRequest> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    private static boolean conflicts(ReservationRequest left, ReservationRequest right) {
        if (left.offhand() && right.offhand()) {
            return true;
        }
        return left.hotbarSlots().stream().anyMatch(right.hotbarSlots()::contains);
    }
}
