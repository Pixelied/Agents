package dev.adrien.crystaloptimizer.execution;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HotbarRestockPlannerTest {
    @Test
    void missingCombatResourceUsesEmptyHotbarSlotAndFullestReserveStack() {
        HotbarRestockPlanner planner = new HotbarRestockPlanner();

        Optional<RestockDecision> decision = planner.choose(List.of(
            new InventoryStackView(0, Items.DIAMOND_SWORD, 1),
            new InventoryStackView(9, Items.END_CRYSTAL, 12),
            new InventoryStackView(18, Items.END_CRYSTAL, 64)
        ));

        RestockDecision restock = decision.orElseThrow();
        assertEquals(18, restock.sourceInventorySlot());
        assertEquals(1, restock.hotbarSlot());
        assertEquals(Items.END_CRYSTAL, restock.item());
    }

    @Test
    void lowCombatStackIsSwappedForLargerReserveWithoutReplacingOtherHotbarItems() {
        HotbarRestockPlanner planner = new HotbarRestockPlanner();

        RestockDecision decision = planner.choose(List.of(
            new InventoryStackView(0, Items.END_CRYSTAL, 3),
            new InventoryStackView(1, Items.DIAMOND_SWORD, 1),
            new InventoryStackView(12, Items.END_CRYSTAL, 48)
        )).orElseThrow();

        assertEquals(12, decision.sourceInventorySlot());
        assertEquals(0, decision.hotbarSlot());
        assertEquals(Items.END_CRYSTAL, decision.item());
    }

    @Test
    void noSwapWhenHotbarAlreadyHasAnEqualOrLargerReadyStack() {
        HotbarRestockPlanner planner = new HotbarRestockPlanner();

        assertTrue(planner.choose(List.of(
            new InventoryStackView(0, Items.END_CRYSTAL, 48),
            new InventoryStackView(9, Items.END_CRYSTAL, 16)
        )).isEmpty());
    }

    @Test
    void fullUnrelatedHotbarIsNeverOverwrittenToIntroduceANewResource() {
        HotbarRestockPlanner planner = new HotbarRestockPlanner();
        var slots = new java.util.ArrayList<InventoryStackView>();
        for (int slot = 0; slot < 9; slot++) {
            slots.add(new InventoryStackView(slot, Items.COBBLESTONE, 64));
        }
        slots.add(new InventoryStackView(9, Items.END_CRYSTAL, 64));

        assertTrue(planner.choose(slots).isEmpty());
    }

    @Test
    void nonCombatItemsAreIgnoredEvenWhenHotbarHasSpace() {
        HotbarRestockPlanner planner = new HotbarRestockPlanner();

        assertTrue(planner.choose(List.of(
            new InventoryStackView(9, Items.DIAMOND, 64)
        )).isEmpty());
    }

    @Test
    void crystalReadinessWinsWhenMultipleMissingCombatResourcesCouldBeRestocked() {
        HotbarRestockPlanner planner = new HotbarRestockPlanner();

        RestockDecision decision = planner.choose(List.of(
            new InventoryStackView(9, Items.GLOWSTONE, 64),
            new InventoryStackView(10, Items.END_CRYSTAL, 16),
            new InventoryStackView(11, Items.RESPAWN_ANCHOR, 64)
        )).orElseThrow();

        assertEquals(Items.END_CRYSTAL, decision.item());
        assertEquals(10, decision.sourceInventorySlot());
        assertEquals(0, decision.hotbarSlot());
    }
}
