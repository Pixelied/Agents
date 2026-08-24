package dev.adrien.crystaloptimizer.v2.reactive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.v2.state.SpawnCrystalCycle;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

final class BreakReplaceOrderingTest {
    @Test
    void observedSpawnMaterializesAttackBeforeReplacement() {
        BlockPos base = new BlockPos(4, 64, 7);
        assertEquals(
            List.of(new AttackKnownCrystal(381), new PlaceCrystal(base)),
            new SpawnCrystalCycle(base, true).materialize(
                new CombatEvent.CrystalSpawned(381, base, 1_000L)
            )
        );
    }
}
