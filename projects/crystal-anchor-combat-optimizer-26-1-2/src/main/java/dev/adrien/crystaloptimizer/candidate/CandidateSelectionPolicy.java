package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import java.util.List;
import java.util.Objects;

public final class CandidateSelectionPolicy {
    private final CandidatePruner pruner;
    private final CandidateBudget budget;

    public CandidateSelectionPolicy(CandidatePruner pruner, CandidateBudget budget) {
        this.pruner = Objects.requireNonNull(pruner, "pruner");
        this.budget = Objects.requireNonNull(budget, "budget");
    }

    public static CandidateSelectionPolicy v3Defaults() {
        return new CandidateSelectionPolicy(
            new CandidatePruner(),
            new CandidateBudget(
                16,
                36,
                10,
                12,
                14,
                8
            )
        );
    }

    public List<Candidate> select(
        CombatState state,
        List<Candidate> generated,
        boolean crystalsEnabled,
        boolean anchorsEnabled
    ) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(generated, "generated");
        List<Candidate> enabled = generated.stream()
            .filter(Objects::nonNull)
            .filter(candidate -> switch (candidate.category()) {
                case CRYSTAL_ATTACK, CRYSTAL_PLACEMENT -> crystalsEnabled;
                case ANCHOR_DETONATION, ANCHOR_SETUP -> anchorsEnabled;
                case SUPPORT_OBSIDIAN, WAIT -> true;
            })
            .toList();
        return pruner.prune(state, enabled, budget, true);
    }
}
