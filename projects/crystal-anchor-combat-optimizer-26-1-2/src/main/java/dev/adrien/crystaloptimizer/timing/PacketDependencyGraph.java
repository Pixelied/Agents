package dev.adrien.crystaloptimizer.timing;

import dev.adrien.crystaloptimizer.action.CombatAction;
import java.util.List;
import java.util.Objects;

public record PacketDependencyGraph(List<PacketDependency> dependencies) {
    public PacketDependencyGraph {
        Objects.requireNonNull(dependencies, "dependencies");
        dependencies = List.copyOf(dependencies);
    }

    public static PacketDependencyGraph fromActions(List<CombatAction> actions) {
        Objects.requireNonNull(actions, "actions");
        return new PacketDependencyGraph(actions.stream().map(CombatAction::dependency).toList());
    }

    public int feedbackBoundaryCount() {
        return (int) dependencies.stream()
            .filter(dependency -> dependency == PacketDependency.SERVER_FEEDBACK_FOR_NEW_ENTITY)
            .count();
    }

    public boolean zeroFeedbackCriticalPath() {
        return feedbackBoundaryCount() == 0;
    }
}
