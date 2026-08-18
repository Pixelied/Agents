package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.SimCombatant;

public record DamageResult(
    SimCombatant target,
    DamageTrace trace,
    boolean accepted,
    boolean uncertain
) {
    public DamageResult(SimCombatant target, DamageTrace trace, boolean accepted) {
        this(target, trace, accepted, false);
    }
}
