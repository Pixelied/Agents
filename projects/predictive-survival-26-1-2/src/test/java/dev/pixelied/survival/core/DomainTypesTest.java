package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomainTypesTest {
    @Test
    void damageRangeRejectsInvertedBounds() {
        assertThrows(IllegalArgumentException.class, () -> new DamageRange(8f, 4f));
    }

    @Test
    void subtractFloorsAtZero() {
        assertEquals(new DamageRange(0f, 3f), new DamageRange(2f, 5f).subtractFloorZero(2f));
    }

    @Test
    void tickWindowOverlapIncludesSharedBoundary() {
        assertTrue(new TickWindow(10, 12).overlaps(new TickWindow(12, 15)));
    }

    @Test
    void damageSourceDefensivelyCopiesFlags() {
        EnumSet<DamageFlag> flags = EnumSet.of(DamageFlag.IS_FIRE);
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(1f),
            flags,
            false,
            1f,
            false,
            Optional.empty(),
            "test"
        );

        flags.add(DamageFlag.BYPASSES_ARMOR);

        assertEquals(Set.of(DamageFlag.IS_FIRE), source.flags());
        assertThrows(UnsupportedOperationException.class, () -> source.flags().add(DamageFlag.IS_FREEZING));
    }
}
