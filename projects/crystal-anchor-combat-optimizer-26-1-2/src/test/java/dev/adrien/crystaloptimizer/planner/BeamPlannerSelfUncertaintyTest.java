package dev.adrien.crystaloptimizer.planner;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.candidate.CandidateFeatureEstimator;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.candidate.CandidatePruner;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;

class BeamPlannerSelfUncertaintyTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000071");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000072");

    @Test
    void unknownProtectedSelfThresholdCannotMakeLethalCrystalLookSafe() {
        KnownCrystal crystal = new KnownCrystal(701, new Vec3(0.5, 65.0, 1.0));
        SimCombatant self = SimCombatant.testPlayer(4.0f)
            .withHurtWindow(HurtWindowState.unknownThreshold(20));
        SimCombatant target = SimCombatant.testPlayer(4.0f);
        Map<UUID, CombatantSpatialState> spatial = Map.of(
            SELF,
            new CombatantSpatialState(
                new Vec3(0.5, 64.0, 0.0),
                new AABB(0.2, 64.0, -0.3, 0.8, 65.8, 0.3),
                Vec3.ZERO
            ),
            TARGET,
            new CombatantSpatialState(
                new Vec3(0.5, 64.0, 2.0),
                new AABB(0.2, 64.0, 1.7, 0.8, 65.8, 2.3),
                Vec3.ZERO
            )
        );
        CombatSnapshot snapshot = new CombatSnapshot(
            701L,
            SELF,
            CombatRegion.empty(),
            Map.of(SELF, self, TARGET, target),
            List.of(crystal),
            Map.of(),
            InventoryState.empty(),
            TimingState.unknown(),
            new LegalitySnapshot(new Vec3(0.5, 65.5, 0.0), 6.0, 6.0, List.of(), false),
            spatial,
            Difficulty.NORMAL
        );
        BeamPlanner planner = new BeamPlanner(
            new CandidateGenerator(CandidateFeatureEstimator.conservative()),
            new CandidatePruner(),
            SimulationServices.defaults(),
            RiskBudget.adaptive()
        );

        CombatPlan plan = planner.plan(
            CombatState.fromSnapshot(snapshot, TARGET),
            new PlannerBudget(8, 1, 50_000_000)
        );

        assertFalse(
            plan.actions().stream().anyMatch(AttackKnownCrystal.class::isInstance),
            "unknown self lastHurt must be evaluated with pessimistic full-hit damage"
        );
    }
}
