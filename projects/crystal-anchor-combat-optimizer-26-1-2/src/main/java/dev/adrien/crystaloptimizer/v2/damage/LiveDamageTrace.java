package dev.adrien.crystaloptimizer.v2.damage;

import dev.adrien.crystaloptimizer.sim.damage.DamageTrace;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import java.util.List;
import java.util.Objects;

public record LiveDamageTrace(
    ExplosionContext explosion,
    DamageEstimate estimate,
    List<DamageTrace> scenarioTraces,
    long geometryRevision,
    long combatRevision
) {
    public LiveDamageTrace {
        Objects.requireNonNull(explosion, "explosion");
        Objects.requireNonNull(estimate, "estimate");
        Objects.requireNonNull(scenarioTraces, "scenarioTraces");
        scenarioTraces = List.copyOf(scenarioTraces);
    }
}
