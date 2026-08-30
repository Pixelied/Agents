package dev.pixelied.survival.inventory;

import dev.pixelied.survival.config.TotemHandPriority;
import dev.pixelied.survival.planner.SurvivalAction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Scores death-protection routes after physical feasibility has been established. Deadline safety
 * and absolute policy permissions sort before every user preference; SMART then minimizes gameplay
 * interruption/displacement rather than blindly preferring one hand.
 */
public final class ProtectionRouteScorer {
    private static final int ACTIVE_USE_INTERRUPTION_COST = 6;
    private static final int ACTIVE_SHIELD_INTERRUPTION_COST = 8;
    private static final int HOTBAR_SELECTION_DISPLACEMENT_COST = 2;
    private static final int CONTAINER_DISPLACEMENT_COST = 1;

    public List<ScoredRoute> rank(List<Candidate> candidates, Context context) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(context, "context");
        List<ScoredRoute> scored = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            Candidate value = Objects.requireNonNull(candidate, "candidate");
            scored.add(new ScoredRoute(value.route(), score(value, context)));
        }
        scored.sort(Comparator.comparing(ScoredRoute::score));
        return List.copyOf(scored);
    }

    public ProtectionRouteScore score(Candidate candidate, Context context) {
        Objects.requireNonNull(candidate, "candidate");
        Objects.requireNonNull(context, "context");
        DeathProtectionRoute.Destination destination = destination(candidate.route());
        boolean mutatesMainHand = destination == DeathProtectionRoute.Destination.MAIN_HAND
            && !(candidate.route() instanceof DeathProtectionRoute.AlreadyInHand);
        boolean allowed = !mutatesMainHand || context.mainHandTakeoverAllowed();
        boolean deadlineSafe = allowed && candidate.authorityCompletionTick() <= candidate.deadlineTick();

        int preferenceCost = switch (context.priority()) {
            case SMART -> 0;
            case OFF_HAND -> destination == DeathProtectionRoute.Destination.OFF_HAND ? 0 : 1;
            case MAIN_HAND -> destination == DeathProtectionRoute.Destination.MAIN_HAND ? 0 : 1;
        };

        int interruptionCost = 0;
        if (destination == DeathProtectionRoute.Destination.OFF_HAND && context.activeOffhandShield()) {
            interruptionCost += ACTIVE_SHIELD_INTERRUPTION_COST;
        }
        if (context.activeUseHand().isPresent() && destinationHand(destination) == context.activeUseHand().get()) {
            interruptionCost += ACTIVE_USE_INTERRUPTION_COST;
        }

        int displacementCost = switch (candidate.route()) {
            case DeathProtectionRoute.HotbarSelect ignored -> HOTBAR_SELECTION_DISPLACEMENT_COST;
            case DeathProtectionRoute.ContainerSwap ignored -> CONTAINER_DISPLACEMENT_COST;
            case DeathProtectionRoute.AlreadyInHand ignored -> 0;
        };

        return new ProtectionRouteScore(
            allowed,
            deadlineSafe,
            candidate.authorityCompletionTick(),
            preferenceCost,
            interruptionCost,
            displacementCost,
            candidate.restorationCost(),
            candidate.userIntentCost()
        );
    }

    /**
     * Compact scalar used only after the planner has independently proven the action survives the
     * timeline. Large radix gaps preserve the same preference/interruption precedence as the score.
     */
    public int disruptionCost(Candidate candidate, Context context) {
        ProtectionRouteScore score = score(candidate, context);
        long encoded = (long) score.preferenceCost() * 100_000L
            + (long) score.interruptionCost() * 10_000L
            + (long) score.userIntentCost() * 1_000L
            + (long) score.displacementCost() * 100L
            + (long) score.restorationCost() * 10L;
        return encoded >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) encoded;
    }

    public static DeathProtectionRoute.Destination destination(DeathProtectionRoute route) {
        Objects.requireNonNull(route, "route");
        if (route instanceof DeathProtectionRoute.HotbarSelect) {
            return DeathProtectionRoute.Destination.MAIN_HAND;
        }
        if (route instanceof DeathProtectionRoute.ContainerSwap swap) return swap.destination();
        return ((DeathProtectionRoute.AlreadyInHand) route).destination();
    }

    private static SurvivalAction.Hand destinationHand(DeathProtectionRoute.Destination destination) {
        return destination == DeathProtectionRoute.Destination.OFF_HAND
            ? SurvivalAction.Hand.OFF_HAND
            : SurvivalAction.Hand.MAIN_HAND;
    }

    public record Candidate(
        DeathProtectionRoute route,
        long authorityCompletionTick,
        long deadlineTick,
        int restorationCost,
        int userIntentCost
    ) {
        public Candidate {
            route = Objects.requireNonNull(route, "route");
            if (authorityCompletionTick < 0L || deadlineTick < 0L) {
                throw new IllegalArgumentException("authority/deadline ticks must be non-negative");
            }
            if (restorationCost < 0 || userIntentCost < 0) {
                throw new IllegalArgumentException("route costs must be non-negative");
            }
        }
    }

    public record Context(
        TotemHandPriority priority,
        boolean activeOffhandShield,
        Optional<SurvivalAction.Hand> activeUseHand,
        boolean mainHandTakeoverAllowed
    ) {
        public Context {
            priority = Objects.requireNonNull(priority, "priority");
            activeUseHand = Objects.requireNonNull(activeUseHand, "activeUseHand");
        }
    }

    public record ScoredRoute(DeathProtectionRoute route, ProtectionRouteScore score) {
        public ScoredRoute {
            route = Objects.requireNonNull(route, "route");
            score = Objects.requireNonNull(score, "score");
        }
    }

    public record ProtectionRouteScore(
        boolean allowed,
        boolean deadlineSafe,
        long authorityCompletionTick,
        int preferenceCost,
        int interruptionCost,
        int displacementCost,
        int restorationCost,
        int userIntentCost
    ) implements Comparable<ProtectionRouteScore> {
        public ProtectionRouteScore {
            if (authorityCompletionTick < 0L) throw new IllegalArgumentException("authorityCompletionTick must be non-negative");
            if (preferenceCost < 0 || interruptionCost < 0 || displacementCost < 0
                || restorationCost < 0 || userIntentCost < 0) {
                throw new IllegalArgumentException("score costs must be non-negative");
            }
        }

        @Override
        public int compareTo(ProtectionRouteScore other) {
            Objects.requireNonNull(other, "other");
            int comparison = Boolean.compare(other.allowed, allowed);
            if (comparison != 0) return comparison;
            comparison = Boolean.compare(other.deadlineSafe, deadlineSafe);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(preferenceCost, other.preferenceCost);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(interruptionCost, other.interruptionCost);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(userIntentCost, other.userIntentCost);
            if (comparison != 0) return comparison;
            comparison = Integer.compare(displacementCost, other.displacementCost);
            if (comparison != 0) return comparison;
            comparison = Long.compare(authorityCompletionTick, other.authorityCompletionTick);
            if (comparison != 0) return comparison;
            return Integer.compare(restorationCost, other.restorationCost);
        }
    }
}
