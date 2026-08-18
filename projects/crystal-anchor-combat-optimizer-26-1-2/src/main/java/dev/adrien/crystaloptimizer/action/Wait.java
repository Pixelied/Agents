package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.timing.PacketDependency;

public record Wait(int ticks) implements CombatAction {
    @Override
    public ActionLegality check(CombatState state) {
        return ticks > 0
            ? ActionLegality.allowed()
            : ActionLegality.denied("wait ticks must be positive");
    }

    @Override
    public ActionOutcome simulate(CombatState state, SimulationServices services) {
        if (!check(state).legal()) {
            return ActionOutcome.impossible(state);
        }

        var nextSelf = state.self().withHurtWindow(state.self().hurtWindow().tick(ticks));
        var nextTarget = state.target().withHurtWindow(state.target().hurtWindow().tick(ticks));
        return ActionOutcome.success(
            state.withSelfAndTarget(nextSelf, nextTarget)
                .withTiming(state.timing().advanceTicks(ticks))
        );
    }

    @Override
    public PacketDependency dependency() {
        return PacketDependency.LOCAL_STATE;
    }
}
