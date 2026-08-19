package dev.adrien.crystaloptimizer.v2.damage;

import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import java.util.Objects;
import java.util.Set;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public record DamageScenario(
    SimCombatant victim,
    Vec3 position,
    AABB box,
    double probabilityWeight,
    double confidence,
    Set<DamageUncertainty> uncertainties
) {
    public DamageScenario {
        Objects.requireNonNull(victim, "victim");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(uncertainties, "uncertainties");
        if (!Double.isFinite(probabilityWeight) || probabilityWeight <= 0.0) {
            throw new IllegalArgumentException("scenario probability weight must be positive");
        }
        if (!Double.isFinite(confidence) || confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("scenario confidence outside [0,1]");
        }
        uncertainties = Set.copyOf(uncertainties);
    }
}
