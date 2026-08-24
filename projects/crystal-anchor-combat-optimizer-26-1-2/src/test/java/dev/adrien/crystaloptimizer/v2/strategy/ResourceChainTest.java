package dev.adrien.crystaloptimizer.v2.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Test;

final class ResourceChainTest {
    @Test
    void completeAnchorChainCarriesAnchorAndGlowstoneDemand() {
        ResourceChain chain = ResourceChain.of(Map.of(
            Items.RESPAWN_ANCHOR, 1,
            Items.GLOWSTONE, 1
        ), 2.5);

        assertEquals(1, chain.count(Items.RESPAWN_ANCHOR));
        assertEquals(1, chain.count(Items.GLOWSTONE));
        assertEquals(0, chain.count(Items.END_CRYSTAL));
        assertEquals(2.5, chain.cost());
    }

    @Test
    void resourceChainDefensivelyCopiesDemand() {
        HashMap<net.minecraft.world.item.Item, Integer> demand = new HashMap<>();
        demand.put(Items.OBSIDIAN, 1);
        demand.put(Items.END_CRYSTAL, 1);

        ResourceChain chain = ResourceChain.of(demand, 2.0);
        demand.clear();

        assertEquals(1, chain.count(Items.OBSIDIAN));
        assertEquals(1, chain.count(Items.END_CRYSTAL));
        assertThrows(UnsupportedOperationException.class,
            () -> chain.demand().put(Items.GLOWSTONE, 1));
    }

    @Test
    void emptyChainHasNoDemandOrCost() {
        ResourceChain chain = ResourceChain.none();

        assertTrue(chain.isEmpty());
        assertEquals(0.0, chain.cost());
    }

    @Test
    void rejectsNonPositiveCountsAndInvalidCost() {
        assertThrows(IllegalArgumentException.class,
            () -> ResourceChain.of(Map.of(Items.END_CRYSTAL, 0), 1.0));
        assertThrows(IllegalArgumentException.class,
            () -> ResourceChain.of(Map.of(Items.END_CRYSTAL, 1), Double.NaN));
        assertThrows(IllegalArgumentException.class,
            () -> ResourceChain.of(Map.of(Items.END_CRYSTAL, 1), -1.0));
    }
}
