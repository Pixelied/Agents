package dev.pixelied.survival.inventory;

import dev.pixelied.survival.config.TotemHandPriority;
import dev.pixelied.survival.planner.SurvivalAction;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectionRouteScorerTest {
    private final ProtectionRouteScorer scorer = new ProtectionRouteScorer();

    @Test
    void offhandPriorityWinsOnlyWhenBothRoutesMeetTheDeadline() {
        var main = candidate(new DeathProtectionRoute.HotbarSelect(5), 101, 103);
        var off = candidate(new DeathProtectionRoute.ContainerSwap(
            26, 40, DeathProtectionRoute.Destination.OFF_HAND
        ), 102, 103);

        var ranked = scorer.rank(List.of(main, off), context(TotemHandPriority.OFF_HAND, false, Optional.empty(), true));

        assertEquals(off.route(), ranked.getFirst().route());
        assertTrue(ranked.getFirst().score().deadlineSafe());
    }

    @Test
    void mainHandPriorityWinsWhenBothRoutesAreSafe() {
        var main = candidate(new DeathProtectionRoute.HotbarSelect(5), 101, 103);
        var off = candidate(new DeathProtectionRoute.ContainerSwap(
            26, 40, DeathProtectionRoute.Destination.OFF_HAND
        ), 102, 103);

        var ranked = scorer.rank(List.of(off, main), context(TotemHandPriority.MAIN_HAND, false, Optional.empty(), true));

        assertEquals(main.route(), ranked.getFirst().route());
    }

    @Test
    void preferredLateRouteNeverBeatsEarlierSafeRoute() {
        var main = candidate(new DeathProtectionRoute.HotbarSelect(5), 102, 102);
        var off = candidate(new DeathProtectionRoute.ContainerSwap(
            26, 40, DeathProtectionRoute.Destination.OFF_HAND
        ), 104, 102);

        var ranked = scorer.rank(List.of(off, main), context(TotemHandPriority.OFF_HAND, false, Optional.empty(), true));

        assertEquals(main.route(), ranked.getFirst().route());
        assertTrue(ranked.getFirst().score().deadlineSafe());
        assertFalse(ranked.get(1).score().deadlineSafe());
    }

    @Test
    void smartAvoidsReplacingActiveOffhandShieldWhenMainRouteIsSafe() {
        var main = candidate(new DeathProtectionRoute.HotbarSelect(5), 102, 103);
        var off = candidate(new DeathProtectionRoute.ContainerSwap(
            26, 40, DeathProtectionRoute.Destination.OFF_HAND
        ), 102, 103);

        var ranked = scorer.rank(List.of(off, main), context(TotemHandPriority.SMART, true, Optional.empty(), true));

        assertEquals(main.route(), ranked.getFirst().route());
        assertTrue(ranked.get(1).score().interruptionCost() > ranked.getFirst().score().interruptionCost());
    }

    @Test
    void smartAvoidsInterruptingActivelyUsedMainHandWhenOffhandIsSafe() {
        var main = candidate(new DeathProtectionRoute.HotbarSelect(5), 102, 103);
        var off = candidate(new DeathProtectionRoute.ContainerSwap(
            26, 40, DeathProtectionRoute.Destination.OFF_HAND
        ), 102, 103);

        var ranked = scorer.rank(
            List.of(main, off),
            context(TotemHandPriority.SMART, false, Optional.of(SurvivalAction.Hand.MAIN_HAND), true)
        );

        assertEquals(off.route(), ranked.getFirst().route());
    }

    @Test
    void mainHandTakeoverFalseIsAnAbsoluteProhibition() {
        var main = candidate(new DeathProtectionRoute.HotbarSelect(5), 101, 103);
        var off = candidate(new DeathProtectionRoute.ContainerSwap(
            26, 40, DeathProtectionRoute.Destination.OFF_HAND
        ), 102, 103);

        var ranked = scorer.rank(List.of(main, off), context(TotemHandPriority.MAIN_HAND, false, Optional.empty(), false));

        assertEquals(off.route(), ranked.getFirst().route());
        assertFalse(ranked.get(1).score().allowed());
    }

    private static ProtectionRouteScorer.Candidate candidate(
        DeathProtectionRoute route,
        long completionTick,
        long deadlineTick
    ) {
        return new ProtectionRouteScorer.Candidate(route, completionTick, deadlineTick, 1, 0);
    }

    private static ProtectionRouteScorer.Context context(
        TotemHandPriority priority,
        boolean activeOffhandShield,
        Optional<SurvivalAction.Hand> activeUseHand,
        boolean mainHandTakeoverAllowed
    ) {
        return new ProtectionRouteScorer.Context(
            priority,
            activeOffhandShield,
            activeUseHand,
            mainHandTakeoverAllowed
        );
    }
}
