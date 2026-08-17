package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;

public record HurtWindowDecision(
    boolean accepted,
    float damageForMitigation,
    HurtWindowState nextState
) {
}
