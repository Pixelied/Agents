package dev.adrien.crystaloptimizer.sim.model;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryStackTruthfulnessTest {
    @Test
    void exhaustingSelectedStackClearsOnlyThatSlotWhileReserveCopiesRemain() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.END_CRYSTAL, 65),
            Map.of(0, Items.END_CRYSTAL, 1, Items.END_CRYSTAL),
            Map.of(0, 1, 1, 64),
            Optional.empty()
        );

        InventoryState next = inventory.consume(Items.END_CRYSTAL, 1);

        assertEquals(64, next.count(Items.END_CRYSTAL));
        assertTrue(next.selectedItem().isEmpty(),
            "aggregate reserve crystals must not keep an exhausted selected stack magically usable");
        assertEquals(0, next.hotbarCount(0));
        assertEquals(64, next.hotbarCount(1));
        assertEquals(Items.END_CRYSTAL, next.hotbarItems().get(1));
    }

    @Test
    void consumingFromSelectedStackDecrementsItsExactQuantity() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.GLOWSTONE, 10),
            Map.of(0, Items.GLOWSTONE),
            Map.of(0, 3),
            Optional.empty()
        );

        InventoryState next = inventory.consume(Items.GLOWSTONE, 1);

        assertEquals(9, next.count(Items.GLOWSTONE));
        assertEquals(2, next.hotbarCount(0));
        assertEquals(Items.GLOWSTONE, next.selectedItem().orElseThrow());
    }

    @Test
    void consumingSingleOffhandStackClearsOnlyOffhandWhileReserveCopiesRemain() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.GLOWSTONE, 5),
            Map.of(1, Items.GLOWSTONE),
            Map.of(1, 4),
            Optional.of(Items.GLOWSTONE)
        );

        InventoryState next = inventory.consume(Items.GLOWSTONE, 1);

        assertEquals(4, next.count(Items.GLOWSTONE));
        assertTrue(next.offhandItem().isEmpty(),
            "consuming the only held offhand glowstone must not leave a phantom hand stack");
        assertEquals(4, next.hotbarCount(1),
            "reserve copies in another slot must remain untouched");
    }

    @Test
    void exactMultiCountOffhandDepletesOneItemAtATime() throws Exception {
        Constructor<InventoryState> constructor = InventoryState.class.getConstructor(
            int.class,
            Map.class,
            Map.class,
            Map.class,
            Optional.class,
            int.class
        );
        Method offhandCount = InventoryState.class.getMethod("offhandCount");
        InventoryState inventory = constructor.newInstance(
            0,
            Map.of(Items.END_CRYSTAL, 5),
            Map.of(1, Items.END_CRYSTAL),
            Map.of(1, 3),
            Optional.of(Items.END_CRYSTAL),
            2
        );

        InventoryState once = inventory.consume(Items.END_CRYSTAL, 1);
        assertEquals(1, offhandCount.invoke(once));
        assertEquals(Items.END_CRYSTAL, once.offhandItem().orElseThrow());
        assertEquals(3, once.hotbarCount(1));

        InventoryState twice = once.consume(Items.END_CRYSTAL, 1);
        assertEquals(0, offhandCount.invoke(twice));
        assertTrue(twice.offhandItem().isEmpty());
        assertEquals(3, twice.hotbarCount(1),
            "offhand consumption must not silently decrement reserve hotbar copies");
    }

    @Test
    void selectingAnotherRealStackAfterExhaustionRestoresUsableHandState() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.RESPAWN_ANCHOR, 5),
            Map.of(0, Items.RESPAWN_ANCHOR, 4, Items.RESPAWN_ANCHOR),
            Map.of(0, 1, 4, 4),
            Optional.empty()
        );

        InventoryState exhausted = inventory.consume(Items.RESPAWN_ANCHOR, 1);
        InventoryState switched = exhausted.withSelectedHotbarSlot(4);

        assertTrue(exhausted.selectedItem().isEmpty());
        assertEquals(Items.RESPAWN_ANCHOR, switched.selectedItem().orElseThrow());
        assertEquals(4, switched.hotbarCount(4));
    }
}
