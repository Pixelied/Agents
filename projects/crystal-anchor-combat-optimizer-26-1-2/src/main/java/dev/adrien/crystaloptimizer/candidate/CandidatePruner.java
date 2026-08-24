package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class CandidatePruner {
    public List<Candidate> prune(CombatState state, List<Candidate> candidates, CandidateBudget budget) {
        return prune(state, candidates, budget, false);
    }

    public List<Candidate> prune(
        CombatState state,
        List<Candidate> candidates,
        CandidateBudget budget,
        boolean preservePositionSensitiveExplosives
    ) {
        List<Candidate> legalAndSafe = candidates.stream()
            .filter(candidate -> candidate.action().check(state).legal())
            .filter(candidate -> !obviousLosingSuicide(state, candidate))
            .toList();

        List<Candidate> nonDominated = new ArrayList<>();
        for (Candidate candidate : legalAndSafe) {
            boolean dominated = legalAndSafe.stream()
                .anyMatch(other -> other != candidate
                    && dominates(other, candidate, preservePositionSensitiveExplosives));
            if (!dominated) {
                nonDominated.add(candidate);
            }
        }

        Map<CandidateCategory, List<Candidate>> byCategory = new EnumMap<>(CandidateCategory.class);
        for (Candidate candidate : nonDominated) {
            byCategory.computeIfAbsent(candidate.category(), ignored -> new ArrayList<>()).add(candidate);
        }

        List<Candidate> kept = new ArrayList<>();
        Comparator<Candidate> ranking = Comparator.comparingDouble(this::score).reversed();
        for (CandidateCategory category : CandidateCategory.values()) {
            List<Candidate> group = byCategory.getOrDefault(category, List.of()).stream()
                .sorted(ranking)
                .toList();
            int quota = Math.min(budget.quota(category), group.size());
            kept.addAll(group.subList(0, quota));
        }
        return List.copyOf(kept);
    }

    private boolean obviousLosingSuicide(CombatState state, Candidate candidate) {
        double selfEffective = state.self().health() + state.self().absorption();
        double targetEffective = state.target().health() + state.target().absorption();
        return candidate.features().selfDamage() >= selfEffective
            && candidate.features().targetDamage() < targetEffective
            && candidate.tacticalInterest() != TacticalInterest.ZERO_FEEDBACK_FINISHER;
    }

    private boolean dominates(
        Candidate left,
        Candidate right,
        boolean preservePositionSensitiveExplosives
    ) {
        if (left.category() != right.category()) {
            return false;
        }
        if (preservePositionSensitiveExplosives && positionSensitive(left.category())) {
            return false;
        }
        if (left.tacticalInterest().priority() < right.tacticalInterest().priority()) {
            return false;
        }

        CandidateFeatures a = left.features();
        CandidateFeatures b = right.features();
        boolean noWorse = a.targetDamage() >= b.targetDamage()
            && a.selfDamage() <= b.selfDamage()
            && a.feedbackBoundaries() <= b.feedbackBoundaries()
            && a.supportActions() <= b.supportActions()
            && a.futureFollowupPotential() >= b.futureFollowupPotential();
        boolean strictlyBetter = a.targetDamage() > b.targetDamage()
            || a.selfDamage() < b.selfDamage()
            || a.feedbackBoundaries() < b.feedbackBoundaries()
            || a.supportActions() < b.supportActions()
            || a.futureFollowupPotential() > b.futureFollowupPotential()
            || left.tacticalInterest().priority() > right.tacticalInterest().priority();
        return noWorse && strictlyBetter;
    }

    private static boolean positionSensitive(CandidateCategory category) {
        return category == CandidateCategory.CRYSTAL_ATTACK
            || category == CandidateCategory.CRYSTAL_PLACEMENT
            || category == CandidateCategory.ANCHOR_DETONATION;
    }

    private double score(Candidate candidate) {
        CandidateFeatures f = candidate.features();
        return f.targetDamage() * 10.0
            + f.futureFollowupPotential() * 20.0
            - f.selfDamage() * 5.0
            - f.feedbackBoundaries() * 10.0
            - f.supportActions() * 2.0
            + candidate.tacticalInterest().priority() * 30.0;
    }
}
