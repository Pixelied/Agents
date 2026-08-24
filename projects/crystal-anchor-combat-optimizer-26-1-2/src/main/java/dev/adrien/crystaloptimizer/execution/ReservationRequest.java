package dev.adrien.crystaloptimizer.execution;

import java.util.Set;

public record ReservationRequest(
    Owner owner,
    int priority,
    boolean offhand,
    Set<Integer> hotbarSlots
) {
    public static final int PRIORITY_AURA = 10;
    public static final int PRIORITY_AUTOTOTEM_EMERGENCY = 100;

    public ReservationRequest {
        if (owner == null) {
            throw new NullPointerException("owner");
        }
        if (priority < 0) {
            throw new IllegalArgumentException("priority must be non-negative");
        }
        hotbarSlots = Set.copyOf(hotbarSlots);
        if (hotbarSlots.stream().anyMatch(slot -> slot < 0 || slot > 8)) {
            throw new IllegalArgumentException("hotbar slots must be in [0, 8]");
        }
    }

    public static ReservationRequest autoTotemEmergency() {
        return new ReservationRequest(Owner.AUTO_TOTEM, PRIORITY_AUTOTOTEM_EMERGENCY, true, Set.of());
    }

    public static ReservationRequest auraOffhand() {
        return new ReservationRequest(Owner.AURA, PRIORITY_AURA, true, Set.of());
    }

    public static ReservationRequest auraHotbar(Set<Integer> slots) {
        return new ReservationRequest(Owner.AURA, PRIORITY_AURA, false, slots);
    }

    public boolean isAutoTotemEmergency() {
        return owner == Owner.AUTO_TOTEM && priority >= PRIORITY_AUTOTOTEM_EMERGENCY;
    }

    public enum Owner {
        AURA,
        AUTO_TOTEM
    }
}
