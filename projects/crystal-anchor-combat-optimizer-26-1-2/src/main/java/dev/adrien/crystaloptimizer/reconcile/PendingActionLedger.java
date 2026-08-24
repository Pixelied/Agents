package dev.adrien.crystaloptimizer.reconcile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PendingActionLedger {
    private final Map<String, PendingAction> actions = new LinkedHashMap<>();
    private final Map<String, boolean[]> confirmations = new HashMap<>();

    public synchronized void add(PendingAction action) {
        Objects.requireNonNull(action, "action");
        if (actions.containsKey(action.id())) {
            throw new IllegalArgumentException("duplicate pending action id: " + action.id());
        }
        actions.put(action.id(), action);
        confirmations.put(action.id(), new boolean[action.expectedObservations().size()]);
    }

    public synchronized PendingAction require(String id) {
        PendingAction action = actions.get(id);
        if (action == null) {
            throw new IllegalArgumentException("unknown pending action: " + id);
        }
        return action;
    }

    public synchronized List<PendingAction> actions() {
        return List.copyOf(actions.values());
    }

    public synchronized void observe(ReconciliationEvent event) {
        Objects.requireNonNull(event, "event");
        for (Map.Entry<String, PendingAction> entry : new ArrayList<>(actions.entrySet())) {
            PendingAction action = entry.getValue();
            if (action.status() != PendingAction.Status.WAITING) {
                continue;
            }

            boolean[] confirmed = confirmations.get(action.id());
            boolean matched = false;
            for (int index = 0; index < action.expectedObservations().size(); index++) {
                if (confirmed[index]) {
                    continue;
                }
                PlanAssumption assumption = action.expectedObservations().get(index);
                if (!assumption.relevant(event)) {
                    continue;
                }
                matched = true;
                if (assumption.failure(event).isPresent()) {
                    actions.put(action.id(), action.withStatus(PendingAction.Status.FAILED));
                    break;
                }
                confirmed[index] = true;
            }

            PendingAction updated = actions.get(action.id());
            if (matched
                && updated.status() == PendingAction.Status.WAITING
                && allConfirmed(confirmed)) {
                actions.put(action.id(), updated.withStatus(PendingAction.Status.CONFIRMED));
            }
        }
    }

    public synchronized void clear() {
        actions.clear();
        confirmations.clear();
    }

    private static boolean allConfirmed(boolean[] confirmed) {
        if (confirmed.length == 0) {
            return false;
        }
        for (boolean value : confirmed) {
            if (!value) {
                return false;
            }
        }
        return true;
    }
}
