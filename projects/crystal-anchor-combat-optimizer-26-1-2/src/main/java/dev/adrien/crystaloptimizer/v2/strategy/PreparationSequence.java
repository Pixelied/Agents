package dev.adrien.crystaloptimizer.v2.strategy;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.reconcile.PlanAssumption;
import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.v2.timing.TimingTransition;
import dev.adrien.crystaloptimizer.world.WorldHypothesis;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;

public record PreparationSequence(
    List<CombatAction> actions,
    ExplosionContext terminalExplosion,
    ResourceChain resources,
    Set<BlockPos> geometryDependencies,
    Optional<WorldHypothesis> worldHypothesis
) {
    public PreparationSequence(
        List<CombatAction> actions,
        ExplosionContext terminalExplosion,
        ResourceChain resources,
        Set<BlockPos> geometryDependencies
    ) {
        this(actions, terminalExplosion, resources, geometryDependencies, Optional.empty());
    }

    public PreparationSequence {
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(terminalExplosion, "terminalExplosion");
        Objects.requireNonNull(resources, "resources");
        Objects.requireNonNull(geometryDependencies, "geometryDependencies");
        Objects.requireNonNull(worldHypothesis, "worldHypothesis");
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("preparation sequence must contain actions");
        }
        actions = List.copyOf(actions);
        geometryDependencies = Set.copyOf(geometryDependencies);
    }

    public PreparationSequence withWorldHypothesis(WorldHypothesis hypothesis) {
        Objects.requireNonNull(hypothesis, "hypothesis");
        return new PreparationSequence(
            actions,
            terminalExplosion,
            resources,
            geometryDependencies,
            Optional.of(hypothesis)
        );
    }

    public Set<PlanAssumption> assumptions() {
        return worldHypothesis.map(WorldHypothesis::assumptions).orElse(Set.of());
    }

    public TimingTransition feedbackBoundary() {
        return worldHypothesis
            .map(WorldHypothesis::feedbackBoundary)
            .orElse(TimingTransition.IMMEDIATE);
    }

    public boolean requiresFeedback() {
        return worldHypothesis.isPresent();
    }
}
