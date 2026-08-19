package dev.adrien.crystaloptimizer.v2.damage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
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

final class DamageEngineTest {
    private final DamageEngine engine = new DamageEngine();

    @Test
    void exactSingleScenarioCollapsesAndUncertainScenariosRemainBounded() {
        UUID selfId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        SimCombatant self = SimCombatant.testPlayer(20.0f);
        SimCombatant target = SimCombatant.testPlayer(20.0f);
        CombatSnapshot snapshot = new CombatSnapshot(
            1L,
            selfId,
            CombatRegion.empty(),
            Map.of(selfId, self, targetId, target),
            List.of(),
            Map.of(),
            InventoryState.empty(),
            TimingState.unknown()
        );
        CombatState state = CombatState.fromSnapshot(snapshot, targetId);
        ExplosionContext explosion = ExplosionContext.crystal(new Vec3(0.0, 1.0, 3.0));
        Vec3 targetPos = new Vec3(0.0, 0.0, 0.0);
        AABB targetBox = new AABB(-0.3, 0.0, -0.3, 0.3, 1.8, 0.3);

        DamageEstimate exact = engine.estimate(
            explosion,
            state,
            List.of(new DamageScenario(target, targetPos, targetBox, 1.0, 1.0, Set.of())),
            7L,
            11L
        );
        assertTrue(exact.exact());
        assertEquals(7L, exact.geometryRevision());
        assertEquals(11L, exact.combatRevision());

        DamageEstimate uncertain = engine.estimate(
            explosion,
            state,
            List.of(
                new DamageScenario(
                    target,
                    targetPos,
                    targetBox,
                    0.7,
                    0.8,
                    Set.of(DamageUncertainty.PREDICTED_POSITION)
                ),
                new DamageScenario(
                    target,
                    new Vec3(0.0, 0.0, -2.0),
                    targetBox.move(0.0, 0.0, -2.0),
                    0.3,
                    0.4,
                    Set.of(DamageUncertainty.HURT_THRESHOLD_UNKNOWN)
                )
            ),
            8L,
            12L
        );
        assertTrue(uncertain.lowerBound() <= uncertain.expected());
        assertTrue(uncertain.expected() <= uncertain.upperBound());
        assertTrue(uncertain.confidence() < 1.0);
        assertTrue(uncertain.uncertainties().contains(DamageUncertainty.PREDICTED_POSITION));
        assertTrue(uncertain.uncertainties().contains(DamageUncertainty.HURT_THRESHOLD_UNKNOWN));
    }
}
