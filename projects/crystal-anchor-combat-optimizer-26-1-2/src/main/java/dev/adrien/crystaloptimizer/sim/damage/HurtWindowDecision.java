package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;

public record HurtWindowDecision(
    boolean accepted,
    float damageForMitigation,
    HurtWindowState nextState,
    boolean uncertain
) {
    public HurtWindowDecision(boolean accepted, float damageForMitigation, HurtWindowState nextState) {
        this(accepted, damageForMitigation, nextState, false);
    }
}
