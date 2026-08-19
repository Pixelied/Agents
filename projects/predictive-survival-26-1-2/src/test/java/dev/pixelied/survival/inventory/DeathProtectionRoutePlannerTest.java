package dev.pixelied.survival.inventory;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathProtectionRoutePlannerTest {
    private final DeathProtectionRoutePlanner planner = new DeathProtectionRoutePlanner();

    @Test
    void hotbarProtectionUsesOnePacketSelectionRoute() {
        InventorySnapshot inventory = inventory(1, false, Map.of(
            1, slot(1, "minecraft:diamond_sword", 1, false),
            5, slot(5, "minecraft:totem_of_undying", 1, true)
        ));

        DeathProtectionRoute route = planner.choose(inventory, menu(Map.of())).orElseThrow();
        assertEquals(new DeathProtectionRoute.HotbarSelect(5), route);
    }

    @Test
    void activeOffhandShieldPrefersMainHandContainerSwap() {
        InventorySnapshot inventory = inventory(1, true, Map.of(
            1, slot(1, "minecraft:diamond_sword", 1, false),
            17, slot(17, "minecraft:totem_of_undying", 1, true),
            40, slot(40, "minecraft:shield", 1, false)
        ));

        DeathProtectionRoute route = planner.choose(inventory, menu(Map.of(17, 26, 1, 37, 40, 45))).orElseThrow();
        DeathProtectionRoute.ContainerSwap swap = assertInstanceOf(DeathProtectionRoute.ContainerSwap.class, route);
        assertEquals(26, swap.sourceMenuSlot());
        assertEquals(1, swap.button());
        assertEquals(DeathProtectionRoute.Destination.MAIN_HAND, swap.destination());
    }

    @Test
    void normalInventoryProtectionPrefersOffhandSwapButtonForty() {
        InventorySnapshot inventory = inventory(1, false, Map.of(
            1, slot(1, "minecraft:diamond_sword", 1, false),
            17, slot(17, "minecraft:totem_of_undying", 1, true)
        ));

        DeathProtectionRoute.ContainerSwap swap = assertInstanceOf(
            DeathProtectionRoute.ContainerSwap.class,
            planner.choose(inventory, menu(Map.of(17, 26, 40, 45))).orElseThrow()
        );
        assertEquals(40, swap.button());
        assertEquals(DeathProtectionRoute.Destination.OFF_HAND, swap.destination());
    }

    @Test
    void missingMenuMappingProducesNoUnsafeInventoryRoute() {
        InventorySnapshot inventory = inventory(1, false, Map.of(
            17, slot(17, "minecraft:totem_of_undying", 1, true)
        ));
        assertTrue(planner.choose(inventory, menu(Map.of())).isEmpty());
    }

    private static InventorySnapshot inventory(int selected, boolean shield, Map<Integer, InventorySlotSnapshot> slots) {
        return new InventorySnapshot(selected, slots, shield);
    }

    private static InventorySlotSnapshot slot(int index, String key, int count, boolean protection) {
        return new InventorySlotSnapshot(index, key, count, protection);
    }

    private static MenuSlotMap menu(Map<Integer, Integer> map) {
        return new MenuSlotMap(0, 7, map);
    }
}
