package dev.adrien.crystaloptimizer.timing;

import dev.adrien.crystaloptimizer.action.CombatAction;
import java.util.List;
import java.util.Objects;

public record PacketDependencyGraph(
    List<PacketDependency> dependencies,
    List<DependencyEdge> edges
) {
    public PacketDependencyGraph {
        Objects.requireNonNull(dependencies, "dependencies");
        Objects.requireNonNull(edges, "edges");
        dependencies = List.copyOf(dependencies);
        edges = List.copyOf(edges);
    }

    public PacketDependencyGraph(List<PacketDependency> dependencies) {
        this(dependencies, List.of());
    }

    public static PacketDependencyGraph fromActions(List<CombatAction> actions) {
        Objects.requireNonNull(actions, "actions");
        List<PacketDependency> dependencies = actions.stream().map(CombatAction::dependency).toList();
        if (actions.size() < 2) {
            return new PacketDependencyGraph(dependencies, List.of());
        }

        java.util.ArrayList<DependencyEdge> edges = new java.util.ArrayList<>();
        for (int index = 1; index < actions.size(); index++) {
            CombatAction previous = actions.get(index - 1);
            CombatAction current = actions.get(index);
            edges.add(new DependencyEdge(
                previous.getClass().getSimpleName() + "#" + (index - 1),
                current.getClass().getSimpleName() + "#" + index,
                current.dependency()
            ));
        }
        return new PacketDependencyGraph(dependencies, edges);
    }

    public static PacketDependencyGraph of(List<DependencyEdge> edges) {
        Objects.requireNonNull(edges, "edges");
        return new PacketDependencyGraph(
            edges.stream().map(DependencyEdge::dependency).toList(),
            edges
        );
    }

    public int feedbackBoundaryCount() {
        return feedbackBoundaries();
    }

    public int feedbackBoundaries() {
        return (int) dependencies.stream()
            .filter(dependency -> dependency == PacketDependency.SERVER_FEEDBACK_FOR_NEW_ENTITY)
            .count();
    }

    public int clientPredictionEdges() {
        return (int) dependencies.stream()
            .filter(dependency -> dependency == PacketDependency.CLIENT_PREDICTION)
            .count();
    }

    public boolean zeroFeedbackCriticalPath() {
        return feedbackBoundaries() == 0;
    }

    public CompletionDistribution completionDistribution(TimingEstimate timing) {
        Objects.requireNonNull(timing, "timing");
        double sameTick = timing.sameTickProbability();
        sameTick *= Math.pow(0.90, clientPredictionEdges());
        if (feedbackBoundaries() > 0) {
            sameTick = 0.0;
        }
        sameTick = clamp01(sameTick);

        double remaining = 1.0 - sameTick;
        double nextShare = feedbackBoundaries() > 0 ? 0.35 : 0.75;
        double nextTick = remaining * nextShare;
        return new CompletionDistribution(sameTick, nextTick, remaining - nextTick);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
