package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.v2.execution.PendingItemLedger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

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

    /**
     * Chooses the least disruptive legal hand/slot route for an item-consuming interaction.
     * Pending item reservations are treated as already spent so routing cannot double-spend them.
     */
    public Optional<InteractionRoute> routeFor(
        CombatAction action,
        InventoryState inventory,
        PendingItemLedger pendingItems,
        OptimizerConfig config
    ) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(pendingItems, "pendingItems");
        Objects.requireNonNull(config, "config");

        Item required = requiredItem(action);
        if (required == null || !featureEnabled(action, config)) {
            return Optional.empty();
        }
        int globallyAvailable = pendingItems.available(required, inventory.count(required));
        if (globallyAvailable <= 0) {
            return Optional.empty();
        }
        return routeForRequiredItem(required, inventory);
    }

    /**
     * Resolves a hand/slot from the player's currently observed inventory only.
     *
     * <p>This is intended for the final vanilla dispatcher after the arbiter and pending-item
     * ledger have already approved/reserved the action. Re-applying reservation accounting here
     * would incorrectly hide the action's own reservation.</p>
     */
    public Optional<InteractionRoute> routeForObserved(
        CombatAction action,
        InventoryState inventory
    ) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(inventory, "inventory");
        Item required = requiredItem(action);
        return required == null
            ? Optional.empty()
            : routeForRequiredItem(required, inventory);
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

    private static Optional<InteractionRoute> routeForRequiredItem(
        Item required,
        InventoryState inventory
    ) {
        if (inventory.offhandItem().filter(required::equals).isPresent()) {
            return Optional.of(InteractionRoute.offhand());
        }
        if (inventory.selectedItem().filter(required::equals).isPresent()
            && inventory.hotbarCount(inventory.selectedHotbarSlot()) > 0) {
            return Optional.of(InteractionRoute.selectedMainhand());
        }

        return inventory.hotbarItems().entrySet().stream()
            .filter(entry -> entry.getValue().equals(required))
            .filter(entry -> inventory.hotbarCount(entry.getKey()) > 0)
            .min(Comparator.comparingInt(Map.Entry::getKey))
            // The slot-change packet and following interaction can be ordered in the same client
            // dispatch; server/profile spacing is modeled separately rather than guessed here.
            .map(entry -> InteractionRoute.selectMainhand(entry.getKey(), 0.0));
    }

    private static Item requiredItem(CombatAction action) {
        if (action instanceof PlaceCrystal) {
            return Items.END_CRYSTAL;
        }
        if (action instanceof PlaceObsidian) {
            return Items.OBSIDIAN;
        }
        if (action instanceof PlaceAnchor) {
            return Items.RESPAWN_ANCHOR;
        }
        if (action instanceof ChargeAnchor) {
            return Items.GLOWSTONE;
        }
        return null;
    }

    private static boolean featureEnabled(CombatAction action, OptimizerConfig config) {
        if (action instanceof PlaceCrystal || action instanceof PlaceObsidian) {
            return config.crystals();
        }
        if (action instanceof PlaceAnchor || action instanceof ChargeAnchor) {
            return config.anchors();
        }
        return true;
    }

    private static boolean conflicts(ReservationRequest left, ReservationRequest right) {
        if (left.offhand() && right.offhand()) {
            return true;
        }
        return left.hotbarSlots().stream().anyMatch(right.hotbarSlots()::contains);
    }
}
