package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.prediction.PredictionSet;
import dev.adrien.crystaloptimizer.world.CombatSnapshot;
import java.util.Objects;
import java.util.UUID;

public record RuntimeFrame(
    CombatSnapshot snapshot,
    UUID targetId,
    PredictionSet predictions
) {
    public RuntimeFrame {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(predictions, "predictions");
        if (!snapshot.combatants().containsKey(targetId)) {
            throw new IllegalArgumentException("target must be present in snapshot");
        }
    }
}
