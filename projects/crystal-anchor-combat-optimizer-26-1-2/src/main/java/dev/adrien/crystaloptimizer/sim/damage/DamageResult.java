package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.SimCombatant;

public record DamageResult(
    SimCombatant target,
    DamageTrace trace,
    boolean accepted
) {
}
