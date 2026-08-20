package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.ChargeAnchor;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceAnchor;
import dev.adrien.crystaloptimizer.action.PlaceCrystal;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.action.SelectHotbarSlot;
import dev.adrien.crystaloptimizer.action.SimulationServices;
import dev.adrien.crystaloptimizer.candidate.Candidate;
import dev.adrien.crystaloptimizer.candidate.CandidateGenerator;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class StrategicPreparationPlanner {
    private final CandidateGenerator candidates;

    public StrategicPreparationPlanner(CandidateGenerator candidates) {
        this.candidates = Objects.requireNonNull(candidates, "candidates");
    }

    public Optional<List<CombatAction>> plan(CombatState state, OptimizerConfig config) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(config, "config");

        List<Candidate> current = candidates.generate(state);

        Optional<Candidate> direct = current.stream()
            .filter(candidate -> directPreparation(candidate.action(), config))
            .max(preparationComparator());
        if (direct.isPresent()) {
            return Optional.of(List.of(direct.orElseThrow().action()));
        }

        return current.stream()
            .filter(candidate -> candidate.action() instanceof SelectHotbarSlot)
            .map(candidate -> unlockedSequence(state, candidate, config))
            .flatMap(Optional::stream)
            .max(Comparator.comparingDouble(PlannedSequence::score))
            .map(PlannedSequence::actions);
    }

    private Optional<PlannedSequence> unlockedSequence(
        CombatState state,
        Candidate selectionCandidate,
        OptimizerConfig config
    ) {
        CombatAction selection = selectionCandidate.action();
        var selected = selection.simulate(state, SimulationServices.defaults());
        if (selected.status() == dev.adrien.crystaloptimizer.action.ActionStatus.IMPOSSIBLE) {
            return Optional.empty();
        }

        return candidates.generate(selected.state()).stream()
            .filter(candidate -> unlockedAfterSelection(candidate.action(), config))
            .max(preparationComparator())
            .map(unlocked -> new PlannedSequence(
                List.of(selection, unlocked.action()),
                score(unlocked)
            ));
    }

    private static boolean directPreparation(CombatAction action, OptimizerConfig config) {
        if (action instanceof PlaceObsidian) {
            return config.crystals();
        }
        if (action instanceof PlaceAnchor || action instanceof ChargeAnchor) {
            return config.anchors();
        }
        return false;
    }

    private static boolean unlockedAfterSelection(CombatAction action, OptimizerConfig config) {
        if (action instanceof PlaceCrystal || action instanceof PlaceObsidian) {
            return config.crystals();
        }
        if (action instanceof PlaceAnchor
            || action instanceof ChargeAnchor
            || action instanceof DetonateAnchor) {
            return config.anchors();
        }
        return false;
    }

    private static Comparator<Candidate> preparationComparator() {
        return Comparator
            .comparingDouble(StrategicPreparationPlanner::score)
            .thenComparingDouble(candidate -> candidate.features().futureFollowupPotential())
            .thenComparingDouble(candidate -> -candidate.features().rotationCostDegrees())
            .thenComparingDouble(candidate -> -candidate.features().targetDistance());
    }

    private static double score(Candidate candidate) {
        CombatAction action = candidate.action();
        double base = switch (action) {
            case DetonateAnchor ignored -> 600.0;
            case PlaceCrystal ignored -> 500.0;
            case ChargeAnchor ignored -> 400.0;
            case PlaceAnchor ignored -> 300.0;
            case PlaceObsidian ignored -> 200.0;
            default -> 0.0;
        };
        return base + candidate.features().futureFollowupPotential();
    }

    private record PlannedSequence(List<CombatAction> actions, double score) {
        private PlannedSequence {
            actions = List.copyOf(actions);
        }
    }
}
