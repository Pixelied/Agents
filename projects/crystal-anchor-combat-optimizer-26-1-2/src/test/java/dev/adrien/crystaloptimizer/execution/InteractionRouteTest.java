package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InteractionRouteTest {
    @Test
    void offhandCrystalAvoidsUnnecessaryHotbarSwap() {
        var fixture = InteractionRouteFixtures.offhandCrystalPlacement();
        InteractionRoute route = new InventoryCoordinator().routeFor(
            fixture.action(),
            fixture.inventory(),
            fixture.ledger(),
            fixture.config()
        ).orElseThrow();

        assertEquals(InteractionHand.OFF_HAND, route.hand());
        assertTrue(route.selectedSlot().isEmpty());
        assertTrue(!route.swapBackRequired());
        assertEquals(0.0, route.estimatedCostMillis(), 1.0e-9);
    }

    @Test
    void observedInventoryRoutingDoesNotDependOnReservationLedger() {
        var fixture = InteractionRouteFixtures.offhandCrystalPlacement();
        InteractionRoute route = new InventoryCoordinator().routeForObserved(
            fixture.action(),
            fixture.inventory()
        ).orElseThrow();

        assertEquals(InteractionHand.OFF_HAND, route.hand());
        assertTrue(route.selectedSlot().isEmpty());
    }

    @Test
    void anchorDetonationUsesMainhandWhenOnlyOffhandHasGlowstone() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.DIAMOND_SWORD, 1, Items.GLOWSTONE, 1),
            Map.of(0, Items.DIAMOND_SWORD),
            Map.of(0, 1),
            Optional.of(Items.GLOWSTONE)
        );

        InteractionRoute route = new InventoryCoordinator().routeForObserved(
            new DetonateAnchor(new BlockPos(1, 64, 1)),
            inventory
        ).orElseThrow();

        assertEquals(InteractionHand.MAIN_HAND, route.hand());
        assertTrue(route.selectedSlot().isEmpty());
    }

    @Test
    void anchorDetonationUsesOffhandWhenSelectedMainhandHasGlowstone() {
        InventoryState inventory = new InventoryState(
            0,
            Map.of(Items.GLOWSTONE, 1, Items.TOTEM_OF_UNDYING, 1),
            Map.of(0, Items.GLOWSTONE),
            Map.of(0, 1),
            Optional.of(Items.TOTEM_OF_UNDYING)
        );

        InteractionRoute route = new InventoryCoordinator().routeForObserved(
            new DetonateAnchor(new BlockPos(1, 64, 1)),
            inventory
        ).orElseThrow();

        assertEquals(InteractionHand.OFF_HAND, route.hand());
        assertTrue(route.selectedSlot().isEmpty());
    }
}
