package dev.adrien.crystaloptimizer.planner;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.candidate.CandidateFeatureEstimator;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.candidate.CandidatePruner;
import dev.adrien.crystaloptimizer.sim.model.AnchorState;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.CombatantSpatialState;
import dev.adrien.crystaloptimizer.sim.model.InventoryState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TimingState;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import dev.adrien.crystaloptimizer.world.CombatRegion;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import dev.adrien.crystaloptimizer.world.LegalitySnapshot;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamPlannerTest {
    private static final UUID SELF = UUID.fromString("00000000-0000-0000-0000-000000000021");
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000000022");
    private static final UUID THREAT = UUID.fromString("00000000-0000-0000-0000-000000000023");

    @Test
    void plannerChoosesLowerFirstHitWhenItEnablesLethalAnchorFollowup() {
        var planner = planner(RiskBudget.adaptive());

        CombatPlan plan = planner.plan(popLockFixture(), new PlannerBudget(24, 4, 50_000_000));

        assertEquals(2, plan.actions().size());
        assertEquals(AttackKnownCrystal.class, plan.actions().get(0).getClass());
        assertEquals(101, ((AttackKnownCrystal) plan.actions().get(0)).entityId());
        assertEquals(DetonateAnchor.class, plan.actions().get(1).getClass());
        assertTrue(plan.lethal());
        assertTrue(plan.score().targetDeathProbability() > 0.9);
        assertTrue(plan.dependencyGraph().zeroFeedbackCriticalPath());
    }

    @Test
    void planScoreUsesHierarchyInsteadOfRawUtilitySoup() {
        var lethal = new PlanScore(false, 1.0, 0.95, 2, 0.8, 0.9, 0, 0.2, 0.1, 3.0);
        var flashyButNonLethal = new PlanScore(false, 0.0, 0.0, Integer.MAX_VALUE, 1.0, 1.0, 0, 0.0, 99.0, 0.0);
        var unacceptableSuicide = new PlanScore(true, 1.0, 1.0, 1, 1.0, 1.0, 0, 1.0, 99.0, 0.0);

        assertTrue(lethal.compareTo(flashyButNonLethal) > 0);
        assertTrue(lethal.compareTo(unacceptableSuicide) > 0);
    }

    @Test
    void targetHysteresisPreventsTinyPriorityFlips() {
        TargetSelector selector = new TargetSelector(0.05, 0.75);
        TargetPriority retained = new TargetPriority(TARGET, 0.80, 0.25, 4.0);
        TargetPriority challenger = new TargetPriority(THREAT, 0.82, 0.25, 4.0);

        TargetPriority selected = selector.select(TARGET, List.of(retained, challenger), RiskBudget.adaptive());

        assertEquals(TARGET, selected.targetId());
    }

    @Test
    void threatWeightCanBeatAMarginallyEasierTarget() {
        TargetSelector selector = new TargetSelector(0.05, 0.75);
        TargetPriority easyButPassive = new TargetPriority(TARGET, 0.80, 0.10, 5.0);
        TargetPriority activeThreat = new TargetPriority(THREAT, 0.65, 0.90, 4.0);

        TargetPriority selected = selector.select(null, List.of(easyButPassive, activeThreat), RiskBudget.adaptive());

        assertEquals(THREAT, selected.targetId());
    }

    @Test
    void safeAdaptiveAndRuthlessRiskCurvesAreOrderedAndAdaptiveOpensUnderThreat() {
        double safe = RiskBudget.safe().maxAcceptableSelfRisk(0.2, 0.9);
        double adaptiveCalm = RiskBudget.adaptive().maxAcceptableSelfRisk(0.2, 0.9);
        double adaptiveThreatened = RiskBudget.adaptive().maxAcceptableSelfRisk(0.95, 0.95);
        double ruthless = RiskBudget.ruthless().maxAcceptableSelfRisk(0.2, 0.9);

        assertTrue(safe < adaptiveCalm);
        assertTrue(adaptiveCalm < ruthless);
        assertTrue(adaptiveThreatened > adaptiveCalm);
        assertTrue(adaptiveThreatened <= ruthless);
    }

    @Test
    void plannerBudgetBoundsDepthAndBeamWidth() {
        var planner = planner(RiskBudget.adaptive());
        CombatPlan plan = planner.plan(popLockFixture(), new PlannerBudget(1, 1, 50_000_000));

        assertTrue(plan.actions().size() <= 1);
        assertFalse(plan.lethal());
    }

    private static BeamPlanner planner(RiskBudget risk) {
        return new BeamPlanner(
            new CandidateGenerator(CandidateFeatureEstimator.conservative()),
            new CandidatePruner(),
            SimulationServices.defaults(),
            risk
        );
    }

    private static CombatState popLockFixture() {
        BlockPos anchorPos = new BlockPos(0, 64, 5);
        KnownCrystal shaped = new KnownCrystal(101, new Vec3(8.5, 65.0, 1.0));
        KnownCrystal greedy = new KnownCrystal(102, new Vec3(7.5, 65.0, 1.0));
        SimCombatant self = SimCombatant.testPlayer(20.0f);
        SimCombatant target = SimCombatant.testPlayer(5.0f).withTotem(TotemState.OFFHAND);
        CombatRegion region = CombatRegion.singleBlock(anchorPos, Blocks.RESPAWN_ANCHOR.defaultBlockState());
        Map<UUID, CombatantSpatialState> spatial = Map.of(
            SELF,
            new CombatantSpatialState(
                new Vec3(0.5, 64.0, -8.0),
                new AABB(0.2, 64.0, -8.3, 0.8, 65.8, -7.7),
                Vec3.ZERO
            ),
            TARGET,
            new CombatantSpatialState(
                new Vec3(0.5, 64.0, 1.0),
                new AABB(0.2, 64.0, 0.7, 0.8, 65.8, 1.3),
                Vec3.ZERO
            )
        );
        var snapshot = new CombatSnapshot(
            77L,
            SELF,
            region,
            Map.of(SELF, self, TARGET, target),
            List.of(shaped, greedy),
            Map.of(anchorPos, new AnchorState(1)),
            InventoryState.empty(),
            TimingState.unknown(),
            new LegalitySnapshot(new Vec3(0.5, 65.5, -8.0), 15.0, 15.0, List.of(), false),
            spatial,
            Difficulty.NORMAL
        );
        return CombatState.fromSnapshot(snapshot, TARGET);
    }
}
