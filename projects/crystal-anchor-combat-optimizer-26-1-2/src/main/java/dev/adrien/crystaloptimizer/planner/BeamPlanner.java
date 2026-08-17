package dev.adrien.crystaloptimizer.planner;

import dev.adrien.crystaloptimizer.action.ActionOutcome;
import dev.adrien.crystaloptimizer.action.ActionStatus;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.candidate.Candidate;
import dev.adrien.crystaloptimizer.candidate.CandidateBudget;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.candidate.CandidatePruner;
import dev.adrien.crystaloptimizer.prediction.PositionHypothesis;
import dev.adrien.crystaloptimizer.prediction.PredictionSet;
import dev.adrien.crystaloptimizer.sim.damage.DamageRequest;
import dev.adrien.crystaloptimizer.sim.damage.DamageResult;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionDamageCalculator26;
import dev.adrien.crystaloptimizer.sim.damage.VanillaDamageSimulator;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.timing.PacketDependency;
import dev.adrien.crystaloptimizer.timing.PacketDependencyGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class BeamPlanner {
    private final CandidateGenerator generator;
    private final CandidatePruner pruner;
    private final SimulationServices services;
    private final RiskBudget riskBudget;

    public BeamPlanner(
        CandidateGenerator generator,
        CandidatePruner pruner,
        SimulationServices services,
        RiskBudget riskBudget
    ) {
        this.generator = Objects.requireNonNull(generator, "generator");
        this.pruner = Objects.requireNonNull(pruner, "pruner");
        this.services = Objects.requireNonNull(services, "services");
        this.riskBudget = Objects.requireNonNull(riskBudget, "riskBudget");
    }

    public CombatPlan plan(CombatState root, PlannerBudget budget) {
        return plan(root, budget, currentPositionPrediction(root));
    }

    public CombatPlan plan(CombatState root, PlannerBudget budget, PredictionSet targetPredictions) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(targetPredictions, "targetPredictions");

        double initialSelfEffective = effectiveHealth(root.self());
        double initialTargetEffective = effectiveHealth(root.target());
        Node rootNode = Node.root(
            root,
            initialSelfEffective,
            initialTargetEffective,
            scoreRoot(root),
            targetPredictions
        );
        Node best = rootNode;
        List<Node> beam = List.of(rootNode);
        long deadline = saturatingAdd(System.nanoTime(), budget.maxNanos());
        CandidateBudget candidateBudget = new CandidateBudget(
            budget.beamWidth(),
            budget.beamWidth(),
            budget.beamWidth(),
            budget.beamWidth(),
            budget.beamWidth(),
            1
        );

        for (int depth = 0; depth < budget.maxDepth(); depth++) {
            List<Node> expanded = new ArrayList<>();
            for (Node node : beam) {
                if (node.state().target().dead()) {
                    continue;
                }
                boolean uncertainPosition = node.predictions().hypotheses().size() > 1
                    || node.predictions().confidence() < 0.999999;
                List<Candidate> candidates = pruner.prune(
                    node.state(),
                    generator.generate(node.state()),
                    candidateBudget,
                    uncertainPosition
                );
                for (Candidate candidate : candidates) {
                    Node child = expand(node, candidate);
                    if (child != null) {
                        expanded.add(child);
                    }
                }
                if (!expanded.isEmpty() && System.nanoTime() >= deadline) {
                    break;
                }
            }

            if (expanded.isEmpty()) {
                break;
            }
            expanded.sort(Comparator.comparing(Node::score).reversed());
            beam = List.copyOf(expanded.subList(0, Math.min(budget.beamWidth(), expanded.size())));
            for (Node node : beam) {
                if (node.score().compareTo(best.score()) > 0) {
                    best = node;
                }
            }
            if (System.nanoTime() >= deadline) {
                break;
            }
        }

        PacketDependencyGraph graph = PacketDependencyGraph.fromActions(best.actions());
        return new CombatPlan(
            best.actions(),
            best.score(),
            graph,
            best.state().target().dead(),
            best.robustness()
        );
    }

    private Node expand(Node parent, Candidate candidate) {
        ActionOutcome outcome = candidate.action().simulate(parent.state(), services);
        if (outcome.status() == ActionStatus.IMPOSSIBLE) {
            return null;
        }

        CombatState next = outcome.state();
        boolean targetPopped = parent.targetPopped();
        double robustness = parent.robustness();
        if (outcome.status() == ActionStatus.UNCERTAIN) {
            robustness *= 0.97;
        }
        if (outcome.expectsNewEntityFeedback()
            || candidate.action().dependency() == PacketDependency.SERVER_FEEDBACK_FOR_NEW_ENTITY) {
            robustness *= 0.75;
        }

        for (ExplosionContext explosion : outcome.scheduledExplosions()) {
            ExplosionApplication applied = applyExplosion(next, explosion, parent.predictions());
            next = applied.state();
            targetPopped |= applied.targetPopped();
            robustness *= applied.positionRobustness();
        }

        List<CombatAction> actions = new ArrayList<>(parent.actions());
        actions.add(candidate.action());
        List<CombatAction> immutableActions = List.copyOf(actions);
        PacketDependencyGraph graph = PacketDependencyGraph.fromActions(immutableActions);
        double selfRisk = selfRisk(parent.initialSelfEffective(), next);
        double targetDeathProbability = next.target().dead() ? robustness : 0.0;
        double threat = inferredThreat(next);
        boolean unacceptableSelfDeath = next.self().dead()
            || selfRisk > riskBudget.maxAcceptableSelfRisk(threat, targetDeathProbability);
        double threatNeutralization = next.target().dead()
            ? 1.0
            : targetPopped
                ? 0.50
                : healthReduction(parent.initialTargetEffective(), effectiveHealth(next.target()));
        double futureGeometry = futureGeometry(next) + candidate.features().futureFollowupPotential();
        PlanScore score = new PlanScore(
            unacceptableSelfDeath,
            targetDeathProbability,
            next.target().dead() && targetPopped ? robustness : 0.0,
            next.target().dead() ? immutableActions.size() : Integer.MAX_VALUE,
            threatNeutralization,
            robustness,
            graph.feedbackBoundaryCount(),
            selfRisk,
            futureGeometry,
            immutableActions.size()
        );

        return new Node(
            next,
            immutableActions,
            score,
            robustness,
            targetPopped,
            parent.initialSelfEffective(),
            parent.initialTargetEffective(),
            parent.predictions()
        );
    }

    private ExplosionApplication applyExplosion(
        CombatState state,
        ExplosionContext explosion,
        PredictionSet predictions
    ) {
        if (!state.hasSpatialState()) {
            return new ExplosionApplication(state, false, 1.0);
        }

        var selfSpatial = state.selfSpatial();
        PositionHypothesis likely = predictions.likely();
        DamageResult likelyTargetResult = simulateTargetExplosion(state, explosion, likely);
        float selfIncoming = ExplosionDamageCalculator26.incoming(
            explosion,
            selfSpatial.boundingBox(),
            selfSpatial.position(),
            state.geometry()
        );
        var selfResult = VanillaDamageSimulator.apply(
            state.self(),
            DamageRequest.explosion(selfIncoming)
                .withDifficulty(state.base().difficulty())
                .withSourcePosition(explosion.center())
        );

        double positionRobustness = positionRobustness(
            state,
            explosion,
            predictions,
            likelyTargetResult
        );
        CombatState next = state.withSelfAndTarget(selfResult.target(), likelyTargetResult.target());
        next = next.withCrystals(removeCrystalsDamagedByExplosion(next, explosion));
        return new ExplosionApplication(
            next,
            likelyTargetResult.trace().totemTriggered(),
            positionRobustness
        );
    }

    private DamageResult simulateTargetExplosion(
        CombatState state,
        ExplosionContext explosion,
        PositionHypothesis hypothesis
    ) {
        var baseSpatial = state.targetSpatial();
        Vec3 offset = hypothesis.position().subtract(baseSpatial.position());
        AABB predictedBox = baseSpatial.boundingBox().move(offset);
        float targetIncoming = ExplosionDamageCalculator26.incoming(
            explosion,
            predictedBox,
            hypothesis.position(),
            state.geometry()
        );
        return VanillaDamageSimulator.apply(
            state.target(),
            DamageRequest.explosion(targetIncoming)
                .withDifficulty(state.base().difficulty())
                .withSourcePosition(explosion.center())
        );
    }

    private double positionRobustness(
        CombatState state,
        ExplosionContext explosion,
        PredictionSet predictions,
        DamageResult likelyResult
    ) {
        if (predictions.hypotheses().size() == 1) {
            return 1.0;
        }

        double initialEffective = effectiveHealth(state.target());
        if (likelyResult.target().dead()) {
            return predictions.hypotheses().stream()
                .filter(hypothesis -> simulateTargetExplosion(state, explosion, hypothesis).target().dead())
                .mapToDouble(PositionHypothesis::weight)
                .sum();
        }
        if (likelyResult.trace().totemTriggered()) {
            return predictions.hypotheses().stream()
                .filter(hypothesis -> {
                    DamageResult result = simulateTargetExplosion(state, explosion, hypothesis);
                    return result.target().dead() || result.trace().totemTriggered();
                })
                .mapToDouble(PositionHypothesis::weight)
                .sum();
        }

        double likelyReduction = initialEffective - effectiveHealth(likelyResult.target());
        if (likelyReduction <= 1.0e-9) {
            return 1.0;
        }

        double weightedRetention = 0.0;
        for (PositionHypothesis hypothesis : predictions.hypotheses()) {
            DamageResult result = simulateTargetExplosion(state, explosion, hypothesis);
            double reduction = initialEffective - effectiveHealth(result.target());
            weightedRetention += hypothesis.weight() * clamp01(reduction / likelyReduction);
        }
        return clamp01(weightedRetention);
    }

    private List<KnownCrystal> removeCrystalsDamagedByExplosion(CombatState state, ExplosionContext explosion) {
        return state.crystals().stream()
            .filter(crystal -> {
                double x = crystal.position().x;
                double y = crystal.position().y;
                double z = crystal.position().z;
                AABB box = new AABB(x - 1.0, y, z - 1.0, x + 1.0, y + 2.0, z + 1.0);
                float incoming = ExplosionDamageCalculator26.incoming(
                    explosion,
                    box,
                    crystal.position(),
                    state.geometry()
                );
                return incoming <= 0.0f;
            })
            .toList();
    }

    private static PredictionSet currentPositionPrediction(CombatState state) {
        Vec3 position = state.hasSpatialState() ? state.targetSpatial().position() : Vec3.ZERO;
        Vec3 velocity = state.hasSpatialState() ? state.targetSpatial().velocity() : Vec3.ZERO;
        return new PredictionSet(
            List.of(new PositionHypothesis(PositionHypothesis.Kind.LIKELY, position, velocity, 1.0)),
            1.0
        );
    }

    private static PlanScore scoreRoot(CombatState root) {
        return PlanScore.root(futureGeometry(root));
    }

    private static double futureGeometry(CombatState state) {
        long chargedAnchors = state.anchors().values().stream().filter(anchor -> anchor.charged()).count();
        return chargedAnchors + state.crystals().size() * 0.05;
    }

    private static double selfRisk(double initialSelfEffective, CombatState state) {
        if (state.self().dead()) {
            return 1.0;
        }
        if (initialSelfEffective <= 0.0) {
            return 1.0;
        }
        return clamp01((initialSelfEffective - effectiveHealth(state.self())) / initialSelfEffective);
    }

    private static double inferredThreat(CombatState state) {
        double effective = effectiveHealth(state.self());
        if (effective <= 6.0) return 0.95;
        if (effective <= 12.0) return 0.65;
        return 0.25;
    }

    private static double healthReduction(double initial, double current) {
        if (initial <= 0.0) return 1.0;
        return clamp01((initial - current) / initial);
    }

    private static double effectiveHealth(dev.adrien.crystaloptimizer.sim.model.SimCombatant combatant) {
        return combatant.health() + combatant.absorption();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static long saturatingAdd(long left, long right) {
        long result = left + right;
        if (((left ^ result) & (right ^ result)) < 0) {
            return Long.MAX_VALUE;
        }
        return result;
    }

    private record ExplosionApplication(
        CombatState state,
        boolean targetPopped,
        double positionRobustness
    ) {
    }

    private record Node(
        CombatState state,
        List<CombatAction> actions,
        PlanScore score,
        double robustness,
        boolean targetPopped,
        double initialSelfEffective,
        double initialTargetEffective,
        PredictionSet predictions
    ) {
        static Node root(
            CombatState state,
            double initialSelfEffective,
            double initialTargetEffective,
            PlanScore score,
            PredictionSet predictions
        ) {
            return new Node(
                state,
                List.of(),
                score,
                predictions.confidence(),
                false,
                initialSelfEffective,
                initialTargetEffective,
                predictions
            );
        }
    }
}
