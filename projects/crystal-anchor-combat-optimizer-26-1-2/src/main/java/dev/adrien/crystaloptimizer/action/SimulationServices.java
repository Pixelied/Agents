package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.damage.ExplosionDamageCalculator26;
import dev.adrien.crystaloptimizer.sim.damage.VanillaDamageSimulator;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record SimulationServices(
    ExplosionDamageCalculator26 explosionDamage,
    VanillaDamageSimulator damageSimulator
) {
    public SimulationServices {
        Objects.requireNonNull(explosionDamage, "explosionDamage");
        Objects.requireNonNull(damageSimulator, "damageSimulator");
    }

    public static SimulationServices defaults() {
        return new SimulationServices(new ExplosionDamageCalculator26(), new VanillaDamageSimulator());
    }

    public ActionOutcome removeCrystalsHitByExplosion(CombatState state, Collection<Integer> entityIds) {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(entityIds, "entityIds");
        Set<Integer> removed = new HashSet<>(entityIds);
        List<dev.adrien.crystaloptimizer.sim.model.KnownCrystal> survivors = state.crystals().stream()
            .filter(crystal -> !removed.contains(crystal.entityId()))
            .toList();
        return ActionOutcome.success(state.withCrystals(survivors));
    }
}
