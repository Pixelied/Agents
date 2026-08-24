package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.timing.PacketDependency;

public record SelectHotbarSlot(int slot) implements CombatAction {
    @Override
    public ActionLegality check(CombatState state) {
        if (slot < 0 || slot > 8) {
            return ActionLegality.denied("hotbar slot must be in [0, 8]");
        }
        return ActionLegality.allowed();
    }

    @Override
    public ActionOutcome simulate(CombatState state, SimulationServices services) {
        if (!check(state).legal()) {
            return ActionOutcome.impossible(state);
        }
        return ActionOutcome.success(state.withInventory(state.inventory().withSelectedHotbarSlot(slot)));
    }

    @Override
    public PacketDependency dependency() {
        return PacketDependency.NONE;
    }
}
