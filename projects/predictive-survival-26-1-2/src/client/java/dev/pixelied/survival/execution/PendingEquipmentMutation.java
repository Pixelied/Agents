package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.Objects;
import java.util.Optional;

/**
 * One already-dispatched client equipment transition whose server processing time is bounded but
 * not necessarily known exactly. The before/after hand snapshots are the physical hand states at
 * the two ends of the transition; optional mitigation snapshots describe an equipment change
 * performed by the same server action (for example using armor from a hand).
 */
public record PendingEquipmentMutation(
    SurvivalAction.Hand hand,
    InventorySlotSnapshot before,
    InventorySlotSnapshot after,
    TickWindow authorityWindow,
    Origin origin,
    long epoch,
    Optional<MitigationSnapshot> mitigationBefore,
    Optional<MitigationSnapshot> mitigationAfter
) {
    public PendingEquipmentMutation(
        SurvivalAction.Hand hand,
        InventorySlotSnapshot before,
        InventorySlotSnapshot after,
        TickWindow authorityWindow,
        Origin origin,
        long epoch
    ) {
        this(hand, before, after, authorityWindow, origin, epoch, Optional.empty(), Optional.empty());
    }

    public PendingEquipmentMutation {
        hand = Objects.requireNonNull(hand, "hand");
        before = Objects.requireNonNull(before, "before");
        after = Objects.requireNonNull(after, "after");
        authorityWindow = Objects.requireNonNull(authorityWindow, "authorityWindow");
        origin = Objects.requireNonNull(origin, "origin");
        mitigationBefore = Objects.requireNonNull(mitigationBefore, "mitigationBefore");
        mitigationAfter = Objects.requireNonNull(mitigationAfter, "mitigationAfter");
        if (epoch < 0L) throw new IllegalArgumentException("epoch must be non-negative");
        if (hand == SurvivalAction.Hand.OFF_HAND
            && (before.inventoryIndex() != 40 || after.inventoryIndex() != 40)) {
            throw new IllegalArgumentException("off-hand mutations must use inventory index 40");
        }
        if (hand == SurvivalAction.Hand.MAIN_HAND
            && (!isHotbar(before.inventoryIndex()) || !isHotbar(after.inventoryIndex()))) {
            throw new IllegalArgumentException("main-hand mutations must use hotbar inventory indices");
        }
        if (mitigationBefore.isPresent() != mitigationAfter.isPresent()) {
            throw new IllegalArgumentException("mitigation before/after must either both be present or both be absent");
        }
        if (origin == Origin.USER && hand == SurvivalAction.Hand.MAIN_HAND
            && before.inventoryIndex() != after.inventoryIndex()) {
            ManualUserIntentTracker.global().observeHotbarSelection(after.inventoryIndex());
        }
    }

    public boolean definitelyBefore(long serverTick) {
        return serverTick < authorityWindow.earliest();
    }

    public boolean definitelyAfter(long serverTick) {
        return serverTick >= authorityWindow.latest();
    }

    public boolean uncertainAt(long serverTick) {
        return !definitelyBefore(serverTick) && !definitelyAfter(serverTick);
    }

    public enum Origin {
        USER,
        USER_MITIGATION,
        EMERGENCY_PROTECTION,
        RESTORE,
        SURVIVAL_ITEM
    }

    private static boolean isHotbar(int index) {
        return index >= 0 && index <= 8;
    }
}
