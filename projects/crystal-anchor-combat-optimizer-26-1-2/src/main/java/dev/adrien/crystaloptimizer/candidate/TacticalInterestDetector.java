package dev.adrien.crystaloptimizer.candidate;

import dev.adrien.crystaloptimizer.action.AttackKnownCrystal;
import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.action.DetonateAnchor;
import dev.adrien.crystaloptimizer.action.PlaceObsidian;
import dev.adrien.crystaloptimizer.sim.model.CombatState;

public final class TacticalInterestDetector {
    public TacticalInterest detect(CombatState state, CombatAction action, CandidateFeatures features) {
        if (action instanceof DetonateAnchor) {
            return TacticalInterest.ZERO_FEEDBACK_FINISHER;
        }
        if (action instanceof AttackKnownCrystal
            && state.anchors().values().stream().anyMatch(anchor -> anchor.charged())) {
            return TacticalInterest.DAMAGE_STAIRCASE;
        }
        if (action instanceof PlaceObsidian && features.futureFollowupPotential() > 0.0) {
            return TacticalInterest.DAMAGE_SHAPING;
        }
        return TacticalInterest.NONE;
    }
}
