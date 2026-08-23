package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TargetEligibilityPolicyTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000006201");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000006202");

    @Test
    void protectedTargetIsRejectedBeforeExactEvaluation() {
        CombatSnapshot snapshot = snapshot(false);
        assertFalse(TargetEligibilityPolicy.isEligible(snapshot, TARGET, Set.of(TARGET)));
        assertTrue(TargetEligibilityPolicy.isEligible(snapshot, TARGET, Set.of()));
    }

    @Test
    void selfAndDeadTargetsAreNeverEligible() {
        assertFalse(TargetEligibilityPolicy.isEligible(snapshot(false), SELF, Set.of()));
        assertFalse(TargetEligibilityPolicy.isEligible(snapshot(true), TARGET, Set.of()));
    }

    private static CombatSnapshot snapshot(boolean deadTarget) {
        SimCombatant target = deadTarget
            ? SimCombatant.testPlayer(0.0f)
            : SimCombatant.testPlayer(20.0f);
        return new CombatSnapshot(
            1L,
            SELF,
            CombatRegion.empty(),
            Map.of(SELF, SimCombatant.testPlayer(20.0f), TARGET, target),
            List.of(),
            Map.of(),
            InventoryState.empty(),
            TimingState.unknown(),
            dev.adrien.crystaloptimizer.world.LegalitySnapshot.unavailable(),
            Map.of(SELF, spatial(0.0), TARGET, spatial(3.0)),
            net.minecraft.world.Difficulty.NORMAL
        );
    }

    private static CombatantSpatialState spatial(double x) {
        return new CombatantSpatialState(
            new Vec3(x, 64.0, 0.0),
            new AABB(x - 0.3, 64.0, -0.3, x + 0.3, 65.8, 0.3),
            Vec3.ZERO
        );
    }
}
