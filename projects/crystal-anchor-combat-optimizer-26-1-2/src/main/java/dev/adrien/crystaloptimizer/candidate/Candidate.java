package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.action.CombatAction;
import java.util.Objects;

public record Candidate(
    CombatAction action,
    CandidateCategory category,
    CandidateFeatures features,
    TacticalInterest tacticalInterest
) {
    public Candidate {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(features, "features");
        Objects.requireNonNull(tacticalInterest, "tacticalInterest");
    }
}
