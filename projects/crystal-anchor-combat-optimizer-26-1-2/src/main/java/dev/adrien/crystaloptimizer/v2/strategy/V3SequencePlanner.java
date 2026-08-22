package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.candidate.CandidateCategory;
import dev.adrien.crystaloptimizer.candidate.CandidateFeatureEstimator;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.candidate.CandidatePruner;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.planner.BeamPlanner;
import dev.adrien.crystaloptimizer.planner.CombatPlan;
import dev.adrien.crystaloptimizer.planner.PlannerBudget;
import dev.adrien.crystaloptimizer.planner.RiskBudget;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.v2.damage.DamageEstimate;
import dev.adrien.crystaloptimizer.v2.damage.DamageUncertainty;
import dev.adrien.crystaloptimizer.v2.state.FixedActionSequence;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import dev.adrien.crystaloptimizer.v2.timing.SequenceTiming;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded strategic sequence adapter around the source-tested pure BeamPlanner.
 * This class is worker-side only; reactive dispatch never performs search.
 */
public final class V3SequencePlanner {
    private static final double DIRECT_LETHAL_PROBABILITY = 0.999;
    private static final double CERTIFIED_SEARCH_LETHAL_PROBABILITY = 0.80;
    private static final double MIN_CERTIFIED_CONFIDENCE = 0.75;

    public PlannedOpportunity plan(
        StrategicSnapshot snapshot,
        UUID targetId,
        DamageMap directMap,
        OptimizerConfig config,
        PlanningBudget budget
    ) {
        return tryPlan(snapshot, targetId, directMap, config, budget)
            .orElseThrow(() -> new IllegalStateException("no bounded strategic sequence was available"));
    }

    public Optional<PlannedOpportunity> tryPlan(
        StrategicSnapshot snapshot,
        UUID targetId,
        DamageMap directMap,
        OptimizerConfig config,
        PlanningBudget budget
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(directMap, "directMap");
        Objects.requireNonNull(config, "config");
        Objects.requireNonNull(budget, "budget");
        if (!targetId.equals(directMap.targetId())) {
            throw new IllegalArgumentException("direct damage map target does not match sequence target");
        }

        Optional<DamageOpportunity> directLethal = bestCertifiedDirectLethal(directMap, config);
        if (directLethal.isPresent()) {
            DamageOpportunity opportunity = directLethal.orElseThrow();
            FixedActionSequence sequence = (FixedActionSequence) opportunity.actionSpec();
            return Optional.of(new PlannedOpportunity(
                sequence,
                opportunity,
                opportunity.timing().expectedMillis(),
                opportunity.timing().hardFeedbackBoundaries(),
                true
            ));
        }

        CombatState root = CombatState.fromSnapshot(snapshot.combat(), targetId);
        int categoryCount = CandidateCategory.values().length;
        int quotaBound = Math.max(1, budget.maxBranchesPerNode() / categoryCount);
        int boundedLegacyBeamWidth = Math.max(1, Math.min(budget.beamWidth(), quotaBound));
        PlannerBudget legacyBudget = new PlannerBudget(
            boundedLegacyBeamWidth,
            budget.maxDepth(),
            budget.remainingNanos(System.nanoTime())
        );
        BeamPlanner planner = new BeamPlanner(
            new CandidateGenerator(CandidateFeatureEstimator.conservative()),
            new CandidatePruner(),
            SimulationServices.defaults(),
            riskBudget(config.strategy())
        );
        CombatPlan plan = planner.plan(root, legacyBudget);
        if (plan.actions().isEmpty() || !allowedByConfig(plan.actions(), config)) {
            return bestFallbackDirect(directMap, config).map(this::fromDirectOpportunity);
        }

        boolean certifiedLethal = plan.lethal()
            && !plan.score().unacceptableSelfDeath()
            && plan.score().targetDeathProbability() >= CERTIFIED_SEARCH_LETHAL_PROBABILITY
            && plan.robustness() >= MIN_CERTIFIED_CONFIDENCE;
        DamageOpportunity terminal = summarizePlan(snapshot, targetId, root, plan, certifiedLethal);
        int hardFeedbackBoundaries = plan.dependencyGraph().feedbackBoundaryCount();
        double expectedCompletionMillis = hardFeedbackBoundaries == 0
            ? 0.0
            : Double.POSITIVE_INFINITY;
        return Optional.of(new PlannedOpportunity(
            new FixedActionSequence(plan.actions()),
            terminal,
            expectedCompletionMillis,
            hardFeedbackBoundaries,
            certifiedLethal
        ));
    }

    private Optional<DamageOpportunity> bestCertifiedDirectLethal(
        DamageMap directMap,
        OptimizerConfig config
    ) {
        return directMap.opportunities().values().stream()
            .filter(DamageOpportunity::lethal)
            .filter(opportunity -> opportunity.actionSpec() instanceof FixedActionSequence)
            .filter(opportunity -> allowedByConfig(
                ((FixedActionSequence) opportunity.actionSpec()).actions(), config
            ))
            .filter(opportunity -> opportunity.targetDamage().killProbability() >= DIRECT_LETHAL_PROBABILITY)
            .filter(opportunity -> opportunity.targetDamage().confidence() >= MIN_CERTIFIED_CONFIDENCE)
            .filter(opportunity -> !opportunity.selfDamage().totemTriggered())
            .filter(opportunity -> opportunity.selfDamage().worstCaseRemainingHealth() > 0.0f)
            .min(Comparator
                .comparingDouble((DamageOpportunity opportunity) -> opportunity.timing().p90Millis())
                .thenComparingDouble(opportunity -> opportunity.timing().expectedMillis())
                .thenComparing(DamageOpportunity::id));
    }

    private Optional<DamageOpportunity> bestFallbackDirect(
        DamageMap directMap,
        OptimizerConfig config
    ) {
        return directMap.opportunities().values().stream()
            .filter(opportunity -> opportunity.actionSpec() instanceof FixedActionSequence)
            .filter(opportunity -> allowedByConfig(
                ((FixedActionSequence) opportunity.actionSpec()).actions(), config
            ))
            .filter(opportunity -> !opportunity.selfDamage().totemTriggered())
            .filter(opportunity -> opportunity.selfDamage().worstCaseRemainingHealth() > 0.0f)
            .max(Comparator
                .comparingDouble((DamageOpportunity opportunity) -> opportunity.targetDamage().killProbability())
                .thenComparingDouble(opportunity -> opportunity.targetDamage().popProbability())
                .thenComparingDouble(opportunity -> opportunity.targetDamage().expected())
                .thenComparing(DamageOpportunity::id));
    }

    private PlannedOpportunity fromDirectOpportunity(DamageOpportunity opportunity) {
        FixedActionSequence sequence = (FixedActionSequence) opportunity.actionSpec();
        boolean certified = opportunity.lethal()
            && opportunity.targetDamage().killProbability() >= DIRECT_LETHAL_PROBABILITY
            && opportunity.targetDamage().confidence() >= MIN_CERTIFIED_CONFIDENCE;
        return new PlannedOpportunity(
            sequence,
            opportunity,
            opportunity.timing().expectedMillis(),
            opportunity.timing().hardFeedbackBoundaries(),
            certified
        );
    }

    private DamageOpportunity summarizePlan(
        StrategicSnapshot snapshot,
        UUID targetId,
        CombatState root,
        CombatPlan plan,
        boolean certifiedLethal
    ) {
        float targetEffective = effectiveHealth(root.target());
        float targetHealth = root.target().health();
        float effectiveLoss = certifiedLethal ? targetEffective : 0.0f;
        float healthLoss = certifiedLethal ? targetHealth : 0.0f;
        Set<DamageUncertainty> uncertainties = plan.robustness() < 0.999999
            ? Set.of(DamageUncertainty.PREDICTED_POSITION)
            : Set.of();
        long targetRevision = snapshot.targetRevisions().getOrDefault(targetId, 0L);
        DamageEstimate damage = new DamageEstimate(
            effectiveLoss,
            effectiveLoss,
            effectiveLoss,
            healthLoss,
            healthLoss,
            healthLoss,
            0.0f,
            0.0f,
            0.0f,
            plan.score().totemDenialProbability(),
            plan.score().targetDeathProbability(),
            plan.robustness(),
            uncertainties,
            snapshot.worldRevision(),
            targetRevision
        );
        float selfEffective = effectiveHealth(root.self());
        float selfLoss = (float) Math.min(
            selfEffective,
            selfEffective * plan.score().selfRisk()
        );
        float selfRemaining = Math.max(0.0f, selfEffective - selfLoss);
        OpportunityIntent intent = certifiedLethal
            ? OpportunityIntent.LETHAL
            : plan.score().totemDenialProbability() > 0.0
                ? OpportunityIntent.POP
                : OpportunityIntent.PRESSURE;
        int hardFeedbackBoundaries = plan.dependencyGraph().feedbackBoundaryCount();
        double expectedMillis = hardFeedbackBoundaries == 0
            ? 0.0
            : Double.POSITIVE_INFINITY;
        return new DamageOpportunity(
            "plan:" + targetId + ":" + Integer.toHexString(plan.actions().hashCode()),
            new FixedActionSequence(plan.actions()),
            damage,
            intent,
            new SelfDamageEstimate(selfLoss, selfRemaining, false),
            ResourceChain.none(),
            new SequenceTiming(
                expectedMillis,
                expectedMillis,
                hardFeedbackBoundaries,
                plan.robustness()
            ),
            certifiedLethal,
            !certifiedLethal && plan.score().totemDenialProbability() > 0.0,
            true,
            Set.of()
        );
    }

    private static RiskBudget riskBudget(OptimizerStrategy strategy) {
        return switch (strategy) {
            case SAFE -> RiskBudget.safe();
            case LETHAL_SPEED -> RiskBudget.adaptive();
            case AGGRESSIVE -> RiskBudget.ruthless();
        };
    }

    private static boolean allowedByConfig(List<CombatAction> actions, OptimizerConfig config) {
        for (CombatAction action : actions) {
            if ((action instanceof AttackKnownCrystal || action instanceof PlaceCrystal)
                && !config.crystals()) {
                return false;
            }
            if ((action instanceof PlaceAnchor || action instanceof ChargeAnchor || action instanceof DetonateAnchor)
                && !config.anchors()) {
                return false;
            }
            if (action instanceof PlaceObsidian && !config.crystals()) {
                return false;
            }
        }
        return true;
    }

    private static float effectiveHealth(dev.adrien.crystaloptimizer.sim.model.SimCombatant combatant) {
        return combatant.health() + combatant.absorption();
    }
}
