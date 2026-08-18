package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;

public final class HurtWindowProcessor {
    public static HurtWindowDecision evaluate(HurtWindowState state, float incoming) {
        if (state.invulnerableTime() > 10) {
            if (!state.lastHurtKnown()) {
                return new HurtWindowDecision(false, 0.0f, state, true);
            }
            if (incoming <= state.lastHurt()) {
                return new HurtWindowDecision(false, 0.0f, state);
            }

            return new HurtWindowDecision(
                true,
                incoming - state.lastHurt(),
                new HurtWindowState(state.invulnerableTime(), incoming)
            );
        }

        return new HurtWindowDecision(
            true,
            incoming,
            new HurtWindowState(20, incoming)
        );
    }

    private HurtWindowProcessor() {
    }
}
