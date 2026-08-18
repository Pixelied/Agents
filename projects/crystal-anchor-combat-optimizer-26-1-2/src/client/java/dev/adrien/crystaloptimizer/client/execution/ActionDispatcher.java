package dev.adrien.crystaloptimizer.client.execution;

import dev.adrien.crystaloptimizer.action.CombatAction;

public interface ActionDispatcher {
    DispatchReceipt dispatch(CombatAction action);
}
