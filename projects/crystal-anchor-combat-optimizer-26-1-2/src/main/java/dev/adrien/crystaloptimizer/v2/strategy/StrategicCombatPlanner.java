package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.prediction.TargetPredictionModel;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.v2.state.StrategicResult;
import dev.adrien.crystaloptimizer.v2.state.StrategicSnapshot;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Pure stateful planner used only by the single strategic worker thread. */
public final class StrategicCombatPlanner implements StrategicComputation {
    private static final long SEQUENCE_SEARCH_BUDGET_NANOS = 10_000_000L;

    private final TargetPredictionModel predictionModel;
    private final StrategicDamageMapBuilder damageMaps;
    private final StrategicTargetSelector selector = new StrategicTargetSelector();
    private final V3SequencePlanner sequencePlanner;
    private UUID stickyTarget;

    public StrategicCombatPlanner() {
        this(new TargetPredictionModel(), new V3SequencePlanner());
    }

    StrategicCombatPlanner(TargetPredictionModel predictionModel) {
        this(predictionModel, new V3SequencePlanner());
    }

    StrategicCombatPlanner(
        TargetPredictionModel predictionModel,
        V3SequencePlanner sequencePlanner
    ) {
        this.predictionModel = Objects.requireNonNull(predictionModel, "predictionModel");
        this.damageMaps = new StrategicDamageMapBuilder(predictionModel);
        this.sequencePlanner = Objects.requireNonNull(sequencePlanner, "sequencePlanner");
    }

    @Override
    public StrategicResult compute(StrategicSnapshot snapshot, OptimizerConfig config) {
        long nowNanos = System.nanoTime();
        return computeWithPlanningDeadline(
            snapshot,
            config,
            saturatingAdd(nowNanos, SEQUENCE_SEARCH_BUDGET_NANOS)
        );
    }

    /**
     * Deterministic replay entry point. Search still uses the production algorithms and bounds,
     * but the wall clock cannot terminate the search early on a slower machine.
     */
    public StrategicResult computeDeterministic(StrategicSnapshot snapshot, OptimizerConfig config) {
        return computeWithPlanningDeadline(snapshot, config, Long.MAX_VALUE);
    }

    private StrategicResult computeWithPlanningDeadline(
        StrategicSnapshot snapshot,
        OptimizerConfig config,
        long planningDeadlineNanos
    ) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(config, "config");
        if (planningDeadlineNanos < 0L) {
            throw new IllegalArgumentException("planningDeadlineNanos must be non-negative");
        }
        predictionModel.observeSnapshot(snapshot);

        List<TargetPreScore> preScores = preScores(snapshot);
        if (preScores.isEmpty()) {
            stickyTarget = null;
            return null;
        }

        Map<UUID, DamageMap> exactMaps = new LinkedHashMap<>();
        var selected = selector.selectBest(
            preScores,
            stickyTarget,
            Set.of(),
            targetId -> exactMaps.computeIfAbsent(
                targetId,
                id -> damageMaps.build(snapshot, id, config)
            )
        );
        if (selected.isEmpty()) {
            return null;
        }

        StrategicTargetSelector.Selection choice = selected.orElseThrow();
        stickyTarget = choice.targetId();
        Optional<PlannedOpportunity> planned = sequencePlanner.tryPlan(
            snapshot,
            choice.targetId(),
            choice.damageMap(),
            config,
            PlanningBudget.defaults(planningDeadlineNanos)
        );
        return new StrategicResult(
            snapshot.snapshotId(),
            snapshot.worldRevision(),
            snapshot.inventoryRevision(),
            snapshot.configRevision(),
            choice.targetId(),
            choice.damageMap(),
            planned
        );
    }

    private List<TargetPreScore> preScores(StrategicSnapshot snapshot) {
        var selfSpatial = snapshot.combat().spatial().get(snapshot.selfId());
        if (selfSpatial == null) {
            return List.of();
        }
        ArrayList<TargetPreScore> scores = new ArrayList<>();
        for (UUID targetId : snapshot.targetRevisions().keySet()) {
            if (!TargetEligibilityPolicy.isEligible(
                snapshot.combat(),
                targetId,
                snapshot.protectedPlayerIds()
            )) {
                continue;
            }
            SimCombatant target = snapshot.combat().combatants().get(targetId);
            var spatial = snapshot.combat().spatial().get(targetId);
            if (target == null || spatial == null) {
                continue;
            }
            scores.add(new TargetPreScore(
                targetId,
                selfSpatial.position().distanceToSqr(spatial.position()),
                target.health() + target.absorption(),
                HurtThresholdEstimate.MAX_CRYSTAL_HARD_INCOMING,
                false,
                targetId.equals(stickyTarget)
            ));
        }
        return List.copyOf(scores);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
