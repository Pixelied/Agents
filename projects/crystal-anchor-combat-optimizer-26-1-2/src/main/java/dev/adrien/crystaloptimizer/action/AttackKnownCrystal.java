package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.sim.model.KnownCrystal;
import dev.adrien.crystaloptimizer.timing.PacketDependency;
import java.util.List;

public record AttackKnownCrystal(int entityId) implements CombatAction {
    @Override
    public ActionLegality check(CombatState state) {
        KnownCrystal crystal = find(state);
        if (crystal == null) {
            return ActionLegality.denied("crystal entity id is not server-observed in this state");
        }
        if (!state.base().legality().withinEntityReach(crystal.position())) {
            return ActionLegality.denied("crystal is outside entity interaction reach");
        }
        return ActionLegality.allowed();
    }

    @Override
    public ActionOutcome simulate(CombatState state, SimulationServices services) {
        KnownCrystal crystal = find(state);
        if (crystal == null || !check(state).legal()) {
            return ActionOutcome.impossible(state);
        }
        List<KnownCrystal> survivors = state.crystals().stream()
            .filter(candidate -> candidate.entityId() != entityId)
            .toList();
        return ActionOutcome.success(
            state.withCrystals(survivors),
            List.of(ExplosionContext.crystal(crystal.position()))
        );
    }

    @Override
    public PacketDependency dependency() {
        return PacketDependency.NONE;
    }

    private KnownCrystal find(CombatState state) {
        return state.crystals().stream()
            .filter(crystal -> crystal.entityId() == entityId)
            .findFirst()
            .orElse(null);
    }
}
