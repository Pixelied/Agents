package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.sim.model.CombatState;

@FunctionalInterface
public interface CandidateFeatureEstimator {
    CandidateFeatures estimate(CombatState state, CombatAction action, CandidateCategory category);

    static CandidateFeatureEstimator conservative() {
        return (state, action, category) -> {
            boolean reachable = action.check(state).legal();
            boolean prepared = category == CandidateCategory.CRYSTAL_ATTACK
                || category == CandidateCategory.ANCHOR_DETONATION;
            int supportActions = switch (category) {
                case CRYSTAL_PLACEMENT, ANCHOR_SETUP, SUPPORT_OBSIDIAN -> 1;
                default -> 0;
            };
            double selfDistance = 0.0;
            if (action instanceof AttackKnownCrystal attack) {
                selfDistance = state.crystals().stream()
                    .filter(crystal -> crystal.entityId() == attack.entityId())
                    .findFirst()
                    .map(crystal -> Math.sqrt(state.base().legality().eyePosition().distanceToSqr(crystal.position())))
                    .orElse(Double.POSITIVE_INFINITY);
            } else if (action instanceof DetonateAnchor detonate) {
                selfDistance = Math.sqrt(state.base().legality().eyePosition().distanceToSqr(detonate.pos().getCenter()));
            }
            double followup = action instanceof DetonateAnchor ? 1.0 : 0.0;
            if (action instanceof AttackKnownCrystal
                && state.anchors().values().stream().anyMatch(anchor -> anchor.charged())) {
                followup = 1.0;
            }
            return new CandidateFeatures(
                Double.POSITIVE_INFINITY,
                selfDistance,
                1.0,
                reachable,
                0.0,
                prepared,
                action.dependency() == dev.adrien.crystaloptimizer.timing.PacketDependency.SERVER_FEEDBACK_FOR_NEW_ENTITY ? 1 : 0,
                supportActions,
                followup,
                0.0,
                0.0
            );
        };
    }
}
