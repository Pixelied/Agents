package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MobMeleeRangeTest {
    private static final AabbSnapshot MOB = new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6);

    @Test
    void horizontalExpansionMatchesMobAttackBoundingBox() {
        AabbSnapshot target = new AabbSnapshot(1.7, 0, 0, 2.3, 1.8, 0.6);
        assertTrue(MobMeleeRange.isWithin(MOB, Optional.empty(), target, 0, 1.2, 0));
        assertFalse(MobMeleeRange.isWithin(MOB, Optional.empty(), target, 0, 1.0, 0));
    }

    @Test
    void minimumRangeCreatesDeadZone() {
        AabbSnapshot target = new AabbSnapshot(0.7, 0, 0, 1.3, 1.8, 0.6);
        assertFalse(MobMeleeRange.isWithin(MOB, Optional.empty(), target, 0.5, 2.0, 0));
        assertTrue(MobMeleeRange.isWithin(MOB, Optional.empty(), target, 0.0, 2.0, 0));
    }

    @Test
    void mountedMobUsesHorizontalUnionWithVehicle() {
        AabbSnapshot vehicle = new AabbSnapshot(-1.0, -0.2, -1.0, 1.6, 1.0, 1.6);
        AabbSnapshot target = new AabbSnapshot(2.0, 0, 0, 2.6, 1.8, 0.6);
        assertFalse(MobMeleeRange.isWithin(MOB, Optional.empty(), target, 0, 0.5, 0));
        assertTrue(MobMeleeRange.isWithin(MOB, Optional.of(vehicle), target, 0, 0.5, 0));
    }

    @Test
    void ravagerDeflationIsAppliedAfterExpansion() {
        AabbSnapshot target = new AabbSnapshot(1.59, 0, 0, 2.2, 1.8, 0.6);
        assertTrue(MobMeleeRange.isWithin(MOB, Optional.empty(), target, 0, 1.0, 0));
        assertFalse(MobMeleeRange.isWithin(MOB, Optional.empty(), target, 0, 1.0, 0.05));
    }
}
