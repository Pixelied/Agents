package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class CollateralSafetyPolicyTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000006301");
    private static final UUID PROTECTED = UUID.fromString("00000000-0000-0000-0000-000000006302");

    @Test
    void certifiedFatalProtectedDamageIsAlwaysRejected() {
        assertFalse(CollateralSafetyPolicy.accepts(
            Map.of(PROTECTED, damage(20.0f, true)),
            snapshot(),
            40.0f
        ));
    }

    @Test
    void worstCaseProtectedDamageMustStayInsideConfiguredBound() {
        assertFalse(CollateralSafetyPolicy.accepts(
            Map.of(PROTECTED, damage(0.6f, false)),
            snapshot(),
            0.5f
        ));
        assertTrue(CollateralSafetyPolicy.accepts(
            Map.of(PROTECTED, damage(0.4f, false)),
            snapshot(),
            0.5f
        ));
    }

    private static CombatSnapshot snapshot() {
        return new CombatSnapshot(
            1L,
            SELF,
            CombatRegion.empty(),
            Map.of(SELF, SimCombatant.testPlayer(20.0f), PROTECTED, SimCombatant.testPlayer(20.0f)),
            List.of(),
            Map.of(),
            InventoryState.empty(),
            TimingState.unknown()
        );
    }

    private static DamageEstimate damage(float value, boolean fatal) {
        return new DamageEstimate(
            value, value, value,
            value, value, value,
            value, value, value,
            0.0,
            fatal ? 1.0 : 0.0,
            1.0,
            Set.of(),
            1L,
            1L
        );
    }
}
