package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.inventory.DeathProtectionRoute;
import dev.pixelied.survival.inventory.DeathProtectionRoutePlanner;
import dev.pixelied.survival.inventory.InventorySlotSnapshot;
import dev.pixelied.survival.inventory.InventorySnapshot;
import dev.pixelied.survival.inventory.MenuSlotMap;
import dev.pixelied.survival.planner.SurvivalAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EquipmentAuthorityProjectionTest {
    @Test
    void hotbarEquipInFlightDoesNotReceivePrematureProtectionCredit() {
        InventorySlotSnapshot sword = slot(1, "minecraft:diamond_sword", false);
        InventorySlotSnapshot totem = totem(5);
        EquipmentAuthorityProjection projection = projection(
            1,
            sword,
            air(40),
            new PendingEquipmentMutation(
                SurvivalAction.Hand.MAIN_HAND,
                sword,
                totem,
                new TickWindow(102, 104),
                PendingEquipmentMutation.Origin.EMERGENCY_PROTECTION,
                1L
            )
        );

        assertEquals(List.of(DeathProtectionSnapshot.none()), projection.feasibleDeathProtectionAt(101));
        assertTrue(projection.feasibleDeathProtectionAt(103).stream().anyMatch(state -> !state.anyHandAvailable()));
        assertTrue(projection.feasibleDeathProtectionAt(103).stream().anyMatch(DeathProtectionSnapshot::mainHandAvailable));
        assertEquals(1, projection.feasibleDeathProtectionAt(104).size());
        assertTrue(projection.feasibleDeathProtectionAt(104).getFirst().mainHandAvailable());
    }

    @Test
    void restoreAwayInFlightIncludesTheAdverseUnprotectedBranch() {
        InventorySlotSnapshot totem = totem(5);
        InventorySlotSnapshot sword = slot(1, "minecraft:diamond_sword", false);
        EquipmentAuthorityProjection projection = projection(
            5,
            totem,
            air(40),
            new PendingEquipmentMutation(
                SurvivalAction.Hand.MAIN_HAND,
                totem,
                sword,
                new TickWindow(202, 204),
                PendingEquipmentMutation.Origin.RESTORE,
                2L
            )
        );

        assertTrue(projection.feasibleDeathProtectionAt(201).getFirst().mainHandAvailable());
        assertTrue(projection.feasibleDeathProtectionAt(203).stream().anyMatch(state -> !state.anyHandAvailable()),
            "a restore packet may already have removed the Totem inside its authority window");
        assertEquals(List.of(DeathProtectionSnapshot.none()), projection.feasibleDeathProtectionAt(204));
    }

    @Test
    void optimisticOffhandSwapDoesNotBecomeGuaranteedBeforeItsAuthorityDeadline() {
        InventorySlotSnapshot sword = slot(1, "minecraft:diamond_sword", false);
        InventorySlotSnapshot shield = slot(40, "minecraft:shield", false);
        InventorySlotSnapshot totem = totem(40);
        EquipmentAuthorityProjection projection = projection(
            1,
            sword,
            shield,
            new PendingEquipmentMutation(
                SurvivalAction.Hand.OFF_HAND,
                shield,
                totem,
                new TickWindow(302, 307),
                PendingEquipmentMutation.Origin.EMERGENCY_PROTECTION,
                3L
            )
        );

        assertEquals(List.of(DeathProtectionSnapshot.none()), projection.feasibleDeathProtectionAt(301));
        assertTrue(projection.feasibleDeathProtectionAt(305).stream().anyMatch(state -> !state.anyHandAvailable()));
        assertTrue(projection.feasibleDeathProtectionAt(305).stream().anyMatch(DeathProtectionSnapshot::offHandAvailable));
        assertTrue(projection.feasibleDeathProtectionAt(307).getFirst().offHandAvailable());
    }

    @Test
    void restoreUncertaintyCannotSuppressRearmAsAlreadyInHand() {
        InventorySlotSnapshot sword = slot(1, "minecraft:diamond_sword", false);
        InventorySlotSnapshot totem = totem(5);
        InventorySnapshot inventory = new InventorySnapshot(5, Map.of(
            1, sword,
            5, totem,
            40, air(40)
        ), false);
        EquipmentAuthorityProjection projection = projection(
            5,
            totem,
            air(40),
            new PendingEquipmentMutation(
                SurvivalAction.Hand.MAIN_HAND,
                totem,
                sword,
                new TickWindow(402, 404),
                PendingEquipmentMutation.Origin.RESTORE,
                4L
            )
        );

        DeathProtectionRoute route = new DeathProtectionRoutePlanner()
            .choose(inventory, new MenuSlotMap(0, 7, Map.of()), projection, 403)
            .orElseThrow();

        assertEquals(new DeathProtectionRoute.HotbarSelect(5), route,
            "the adverse restore branch must re-arm the exact Totem instead of claiming AlreadyInHand");
        assertInstanceOf(DeathProtectionRoute.HotbarSelect.class, route);
    }

    @Test
    void nonProtectionContentUncertaintyPreservesObservedRouteIdentity() {
        InventorySlotSnapshot sword = slot(0, "minecraft:diamond_sword", false);
        InventorySlotSnapshot chestplate = slot(0, "minecraft:netherite_chestplate", false);
        InventorySlotSnapshot sourceAfter = slot(10, "minecraft:diamond_sword", false);
        InventorySnapshot observed = new InventorySnapshot(0, Map.of(
            0, chestplate,
            10, sourceAfter,
            40, air(40)
        ), false);
        EquipmentAuthorityProjection projection = projection(
            0,
            sword,
            air(40),
            new PendingEquipmentMutation(
                SurvivalAction.Hand.MAIN_HAND,
                sword,
                chestplate,
                new TickWindow(502, 506),
                PendingEquipmentMutation.Origin.USER,
                5L
            )
        );

        InventorySnapshot conservative = projection.conservativeInventoryAt(observed, 504);

        assertEquals("minecraft:netherite_chestplate", conservative.slot(0).orElseThrow().stackKey(),
            "death-protection conservatism must not replace an observed non-protection route item with an arbitrary feasible branch");
        assertEquals("minecraft:diamond_sword", conservative.slot(10).orElseThrow().stackKey());
    }

    private static EquipmentAuthorityProjection projection(
        int selected,
        InventorySlotSnapshot main,
        InventorySlotSnapshot off,
        PendingEquipmentMutation pending
    ) {
        return new EquipmentAuthorityProjection(selected, main, off, List.of(pending), pending.epoch());
    }

    private static InventorySlotSnapshot totem(int index) {
        return new InventorySlotSnapshot(
            index,
            "minecraft:totem_of_undying",
            "minecraft:totem_of_undying".hashCode(),
            1,
            true,
            Optional.empty(),
            Optional.empty(),
            Optional.empty(),
            Optional.of(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()),
            false
        );
    }

    private static InventorySlotSnapshot slot(int index, String key, boolean protection) {
        return new InventorySlotSnapshot(index, key, 1, protection);
    }

    private static InventorySlotSnapshot air(int index) {
        return new InventorySlotSnapshot(index, "minecraft:air", 0, false);
    }
}
