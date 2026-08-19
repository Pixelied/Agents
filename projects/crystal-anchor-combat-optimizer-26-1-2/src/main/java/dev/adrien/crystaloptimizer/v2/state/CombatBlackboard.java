package dev.adrien.crystaloptimizer.v2.state;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class CombatBlackboard {
    private final AtomicReference<CombatBlackboardSnapshot> snapshot =
        new AtomicReference<>(CombatBlackboardSnapshot.empty());

    public CombatBlackboardSnapshot snapshot() {
        return snapshot.get();
    }

    public void publish(CombatBlackboardSnapshot next) {
        snapshot.set(Objects.requireNonNull(next, "next"));
    }
}
