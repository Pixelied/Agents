package dev.adrien.crystaloptimizer.v2.state;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import java.util.List;

public record FixedActionSequence(List<CombatAction> actions) implements ReactiveActionSpec {
    public FixedActionSequence {
        actions = List.copyOf(actions);
        if (actions.isEmpty()) {
            throw new IllegalArgumentException("empty action sequence");
        }
    }

    @Override
    public List<CombatAction> materialize(CombatEvent event) {
        return actions;
    }
}
