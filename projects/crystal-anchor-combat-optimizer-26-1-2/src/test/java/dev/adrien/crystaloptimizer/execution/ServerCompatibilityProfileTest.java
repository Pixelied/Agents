package dev.adrien.crystaloptimizer.execution;

import dev.adrien.crystaloptimizer.v2.timing.TimingSnapshot;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ServerCompatibilityProfileTest {
    @Test
    void compatibilityProfilesCanOnlyTightenVanillaAuthority() {
        TimingSnapshot timing = TimingSnapshot.empty(1_000L);
        CompatibilityConstraints vanilla = ServerCompatibilityProfile.VANILLA.constraints(timing, 0);

        for (ServerCompatibilityProfile profile : ServerCompatibilityProfile.values()) {
            CompatibilityConstraints constraints = profile.constraints(timing, 3);
            assertTrue(constraints.minimumSwapSpacingNanos() >= vanilla.minimumSwapSpacingNanos());
            assertTrue(constraints.minimumInteractionSpacingNanos() >= vanilla.minimumInteractionSpacingNanos());
        }

        Set<String> components = Arrays.stream(CompatibilityConstraints.class.getRecordComponents())
            .map(RecordComponent::getName)
            .collect(Collectors.toSet());
        assertEquals(Set.of(
            "requireVisibleFace",
            "requireFullRotation",
            "minimumSwapSpacingNanos",
            "minimumInteractionSpacingNanos"
        ), components);
    }

    @Test
    void adaptiveStartsAtVanillaAndOnlyTightensAfterObservedRejections() {
        TimingSnapshot timing = TimingSnapshot.empty(2_000L);
        CompatibilityConstraints vanilla = ServerCompatibilityProfile.VANILLA.constraints(timing, 0);
        CompatibilityConstraints adaptiveClean = ServerCompatibilityProfile.ADAPTIVE.constraints(timing, 0);
        CompatibilityConstraints adaptiveRejected = ServerCompatibilityProfile.ADAPTIVE.constraints(timing, 3);

        assertEquals(vanilla, adaptiveClean);
        assertTrue(adaptiveRejected.minimumSwapSpacingNanos() >= adaptiveClean.minimumSwapSpacingNanos());
        assertTrue(adaptiveRejected.minimumInteractionSpacingNanos() >= adaptiveClean.minimumInteractionSpacingNanos());
    }
}
