package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.timing.PacketDependency;

public record Rotate(float yaw, float pitch) implements CombatAction {
    @Override
    public ActionLegality check(CombatState state) {
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            return ActionLegality.denied("rotation must be finite");
        }
        if (pitch < -90.0f || pitch > 90.0f) {
            return ActionLegality.denied("pitch must be in [-90, 90]");
        }
        return ActionLegality.allowed();
    }

    @Override
    public ActionOutcome simulate(CombatState state, SimulationServices services) {
        return check(state).legal() ? ActionOutcome.success(state) : ActionOutcome.impossible(state);
    }

    @Override
    public PacketDependency dependency() {
        return PacketDependency.LOCAL_STATE;
    }
}
