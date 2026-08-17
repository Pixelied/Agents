package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.damage.ExplosionContext;
import dev.adrien.crystaloptimizer.sim.model.CombatState;
import java.util.List;
import java.util.Objects;

public record ActionOutcome(
    CombatState state,
    ActionStatus status,
    List<ExplosionContext> scheduledExplosions,
    boolean expectsNewEntityFeedback
) {
    public ActionOutcome {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(scheduledExplosions, "scheduledExplosions");
        scheduledExplosions = List.copyOf(scheduledExplosions);
    }

    public static ActionOutcome success(CombatState state) {
        return new ActionOutcome(state, ActionStatus.SUCCESS, List.of(), false);
    }

    public static ActionOutcome success(CombatState state, List<ExplosionContext> explosions) {
        return new ActionOutcome(state, ActionStatus.SUCCESS, explosions, false);
    }

    public static ActionOutcome uncertain(
        CombatState state,
        List<ExplosionContext> explosions,
        boolean expectsNewEntityFeedback
    ) {
        return new ActionOutcome(state, ActionStatus.UNCERTAIN, explosions, expectsNewEntityFeedback);
    }

    public static ActionOutcome impossible(CombatState state) {
        return new ActionOutcome(state, ActionStatus.IMPOSSIBLE, List.of(), false);
    }
}
