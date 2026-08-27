package dev.pixelied.survival.execution;

import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded projection of equipment states that can be authoritative on the server while client
 * equipment packets are in flight. Decision code must use the feasible set/worst feasible branch
 * rather than whichever optimistic stack the local player object currently renders.
 */
public record EquipmentAuthorityProjection(
    int confirmedSelectedHotbarIndex,
    InventorySlotSnapshot confirmedMainHand,
    InventorySlotSnapshot confirmedOffHand,
    List<PendingEquipmentMutation> pending,
    long epoch,
    MitigationSnapshot confirmedMitigation
) {
    private static final int MAX_FEASIBLE_STATES = 32;

    /** Planned five-argument API; mitigation is conservative-none until a runtime supplies it. */
    public EquipmentAuthorityProjection(
        int confirmedSelectedHotbarIndex,
        InventorySlotSnapshot confirmedMainHand,
        InventorySlotSnapshot confirmedOffHand,
        List<PendingEquipmentMutation> pending,
        long epoch
    ) {
        this(
            confirmedSelectedHotbarIndex,
            confirmedMainHand,
            confirmedOffHand,
            pending,
            epoch,
            MitigationSnapshot.none()
        );
    }

    public EquipmentAuthorityProjection {
        if (confirmedSelectedHotbarIndex < 0 || confirmedSelectedHotbarIndex > 8) {
            throw new IllegalArgumentException("confirmedSelectedHotbarIndex must be in [0, 8]");
        }
        confirmedMainHand = Objects.requireNonNull(confirmedMainHand, "confirmedMainHand");
        confirmedOffHand = Objects.requireNonNull(confirmedOffHand, "confirmedOffHand");
        confirmedMitigation = Objects.requireNonNull(confirmedMitigation, "confirmedMitigation");
        if (confirmedMainHand.inventoryIndex() != confirmedSelectedHotbarIndex) {
            throw new IllegalArgumentException("confirmed main-hand snapshot must use the confirmed selected index");
        }
        if (confirmedOffHand.inventoryIndex() != 40) {
            throw new IllegalArgumentException("confirmed off-hand snapshot must use inventory index 40");
        }
        if (epoch < 0L) throw new IllegalArgumentException("epoch must be non-negative");

        List<PendingEquipmentMutation> copy = new ArrayList<>(Objects.requireNonNull(pending, "pending"));
        copy.sort(Comparator.comparingLong(PendingEquipmentMutation::epoch));
        long previous = -1L;
        for (PendingEquipmentMutation mutation : copy) {
            if (mutation.epoch() <= previous) {
                throw new IllegalArgumentException("pending equipment mutation epochs must be unique and increasing");
            }
            if (mutation.epoch() > epoch) {
                throw new IllegalArgumentException("pending mutation epoch cannot exceed projection epoch");
            }
            previous = mutation.epoch();
        }
        pending = List.copyOf(copy);
    }

    /** All death-protection hand states that can be authoritative at the supplied server tick. */
    public List<DeathProtectionSnapshot> feasibleDeathProtectionAt(long serverTick) {
        LinkedHashSet<DeathProtectionSnapshot> result = new LinkedHashSet<>();
        for (FeasibleState state : feasibleStatesAt(serverTick)) {
            result.add(new DeathProtectionSnapshot(
                protectionItem(state.mainHand()),
                protectionItem(state.offHand())
            ));
        }
        return List.copyOf(result);
    }

    /** All mitigation states that can be authoritative at the supplied server tick. */
    public List<MitigationSnapshot> feasibleMitigationAt(long serverTick) {
        LinkedHashSet<MitigationSnapshot> result = new LinkedHashSet<>();
        for (FeasibleState state : feasibleStatesAt(serverTick)) result.add(state.mitigation());
        return List.copyOf(result);
    }

    /**
     * Death protection that is present in the same physical hand in every feasible branch. If hand
     * identity or the exact protection component differs across branches, it is not credited.
     */
    public DeathProtectionSnapshot guaranteedDeathProtectionAt(long serverTick) {
        List<DeathProtectionSnapshot> feasible = feasibleDeathProtectionAt(serverTick);
        if (feasible.isEmpty()) return DeathProtectionSnapshot.none();
        Optional<DeathProtectionSnapshot.ProtectionItem> main = guaranteedHand(feasible, true);
        Optional<DeathProtectionSnapshot.ProtectionItem> off = guaranteedHand(feasible, false);
        return new DeathProtectionSnapshot(main, off);
    }

    /**
     * Returns the feasible inventory branch with the least death protection for rescue routing.
     * This prevents an uncertain restore/equip from suppressing a rescue as AlreadyInHand.
     */
    public InventorySnapshot conservativeInventoryAt(InventorySnapshot observed, long serverTick) {
        Objects.requireNonNull(observed, "observed");
        FeasibleState adverse = feasibleStatesAt(serverTick).stream()
            .min(Comparator
                .comparingInt(EquipmentAuthorityProjection::protectionCount)
                .thenComparingInt(state -> state.mainHand().inventoryIndex()))
            .orElse(new FeasibleState(confirmedMainHand, confirmedOffHand, confirmedMitigation));

        Map<Integer, InventorySlotSnapshot> slots = new LinkedHashMap<>(observed.slots());
        slots.put(adverse.mainHand().inventoryIndex(), adverse.mainHand());
        slots.put(40, adverse.offHand());
        boolean offhandShieldStillActive = observed.activeOffhandShield()
            && "minecraft:shield".equals(adverse.offHand().stackKey())
            && adverse.offHand().count() > 0;
        return new InventorySnapshot(
            adverse.mainHand().inventoryIndex(),
            slots,
            offhandShieldStillActive
        );
    }

    private List<FeasibleState> feasibleStatesAt(long serverTick) {
        if (serverTick < 0L) throw new IllegalArgumentException("serverTick must be non-negative");
        List<FeasibleState> states = List.of(new FeasibleState(
            confirmedMainHand,
            confirmedOffHand,
            confirmedMitigation
        ));

        for (PendingEquipmentMutation mutation : pending) {
            List<FeasibleState> next = new ArrayList<>(Math.min(MAX_FEASIBLE_STATES, states.size() * 2));
            for (FeasibleState state : states) {
                if (mutation.definitelyBefore(serverTick)) {
                    addDistinct(next, state);
                } else if (mutation.definitelyAfter(serverTick)) {
                    addDistinct(next, apply(state, mutation));
                } else {
                    addDistinct(next, state);
                    addDistinct(next, apply(state, mutation));
                }
                if (next.size() >= MAX_FEASIBLE_STATES) break;
            }
            states = List.copyOf(next);
        }
        return states;
    }

    private static FeasibleState apply(FeasibleState state, PendingEquipmentMutation mutation) {
        InventorySlotSnapshot main = state.mainHand();
        InventorySlotSnapshot off = state.offHand();
        if (mutation.hand() == SurvivalAction.Hand.MAIN_HAND) main = mutation.after();
        else off = mutation.after();
        MitigationSnapshot mitigation = mutation.mitigationAfter().orElse(state.mitigation());
        return new FeasibleState(main, off, mitigation);
    }

    private static void addDistinct(List<FeasibleState> states, FeasibleState candidate) {
        if (states.size() >= MAX_FEASIBLE_STATES || states.contains(candidate)) return;
        states.add(candidate);
    }

    private static Optional<DeathProtectionSnapshot.ProtectionItem> protectionItem(InventorySlotSnapshot slot) {
        if (slot == null || slot.count() <= 0 || !slot.deathProtection()) return Optional.empty();
        return slot.deathProtectionItem().or(() -> Optional.of(
            "minecraft:totem_of_undying".equals(slot.stackKey())
                ? DeathProtectionSnapshot.ProtectionItem.vanillaTotem()
                : DeathProtectionSnapshot.ProtectionItem.generic()
        ));
    }

    private static Optional<DeathProtectionSnapshot.ProtectionItem> guaranteedHand(
        List<DeathProtectionSnapshot> feasible,
        boolean main
    ) {
        Optional<DeathProtectionSnapshot.ProtectionItem> first = main
            ? feasible.getFirst().mainHand()
            : feasible.getFirst().offHand();
        if (first.isEmpty()) return Optional.empty();
        for (int i = 1; i < feasible.size(); i++) {
            Optional<DeathProtectionSnapshot.ProtectionItem> candidate = main
                ? feasible.get(i).mainHand()
                : feasible.get(i).offHand();
            if (!first.equals(candidate)) return Optional.empty();
        }
        return first;
    }

    private static int protectionCount(FeasibleState state) {
        int count = 0;
        if (protectionItem(state.mainHand()).isPresent()) count++;
        if (protectionItem(state.offHand()).isPresent()) count++;
        return count;
    }

    private record FeasibleState(
        InventorySlotSnapshot mainHand,
        InventorySlotSnapshot offHand,
        MitigationSnapshot mitigation
    ) {
    }
}
