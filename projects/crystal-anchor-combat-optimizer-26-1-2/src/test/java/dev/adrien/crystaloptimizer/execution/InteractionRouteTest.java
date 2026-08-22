package dev.adrien.crystaloptimizer.execution;

import net.minecraft.world.InteractionHand;
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
}
