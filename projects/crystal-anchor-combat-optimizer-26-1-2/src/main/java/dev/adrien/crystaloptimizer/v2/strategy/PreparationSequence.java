package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import net.minecraft.core.BlockPos;

public record PreparationSequence(
    List<CombatAction> actions,
    ExplosionContext terminalExplosion,
    ResourceChain resources,
    Set<BlockPos> geometryDependencies
) {
    public PreparationSequence {
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(terminalExplosion, "terminalExplosion");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(geometryDependencies, "geometryDependencies");
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("preparation sequence must contain actions");
        }
        actions = List.copyOf(actions);
        geometryDependencies = Set.copyOf(geometryDependencies);
    }
}
