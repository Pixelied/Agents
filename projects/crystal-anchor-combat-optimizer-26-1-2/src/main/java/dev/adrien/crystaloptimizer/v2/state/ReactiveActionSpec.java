package dev.adrien.crystaloptimizer.v2.state;

import dev.adrien.crystaloptimizer.action.CombatAction;
import dev.adrien.crystaloptimizer.v2.reactive.CombatEvent;
import java.util.List;

public sealed interface ReactiveActionSpec permits FixedActionSequence, SpawnCrystalCycle {
    List<CombatAction> materialize(CombatEvent event);
}
