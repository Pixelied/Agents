package dev.adrien.crystaloptimizer.client;

import dev.adrien.crystaloptimizer.client.intel.TargetMotionTracker;
import dev.adrien.crystaloptimizer.prediction.MovementSample;
import java.util.List;
import java.util.UUID;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TargetMotionTrackerTest {
    private static final UUID TARGET = UUID.fromString("00000000-0000-0000-0000-000000009001");

    @Test
    void historyIsBoundedToTwelveNewestSamplesAndImmutable() {
        TargetMotionTracker tracker = new TargetMotionTracker();
        for (int index = 0; index < 20; index++) {
            double x = index * 0.1;
            tracker.observe(
                TARGET,
                new Vec3(x, 64.0, 0.0),
                boxAt(x),
                new Vec3(0.1, 0.0, 0.0),
                false,
                index * 50_000_000L
            );
        }

        List<MovementSample> history = tracker.snapshot(TARGET);
        assertEquals(12, history.size());
        assertEquals(8L * 50_000_000L, history.getFirst().timestampNanos());
        assertEquals(19L * 50_000_000L, history.getLast().timestampNanos());
        assertThrows(UnsupportedOperationException.class,
            () -> history.add(new MovementSample(2_000_000_000L, Vec3.ZERO, Vec3.ZERO)));
    }

    @Test
    void teleportSizedCorrectionResetsHistoryConfidence() {
        TargetMotionTracker tracker = new TargetMotionTracker();
        tracker.observe(TARGET, new Vec3(0.0, 64.0, 0.0), boxAt(0.0), Vec3.ZERO, false, 0L);
        tracker.observe(TARGET, new Vec3(0.2, 64.0, 0.0), boxAt(0.2), new Vec3(0.2, 0.0, 0.0), false, 50_000_000L);
        tracker.observe(TARGET, new Vec3(20.2, 64.0, 0.0), boxAt(20.2), Vec3.ZERO, true, 100_000_000L);

        List<MovementSample> history = tracker.snapshot(TARGET);
        assertEquals(1, history.size());
        assertEquals(20.2, history.getFirst().position().x, 1.0e-9);
    }

    @Test
    void removeAndWorldClearDropTrackedState() {
        TargetMotionTracker tracker = new TargetMotionTracker();
        UUID other = UUID.fromString("00000000-0000-0000-0000-000000009002");
        tracker.observe(TARGET, Vec3.ZERO, boxAt(0.0), Vec3.ZERO, false, 0L);
        tracker.observe(other, Vec3.ZERO, boxAt(0.0), Vec3.ZERO, false, 0L);

        tracker.remove(TARGET);
        assertTrue(tracker.snapshot(TARGET).isEmpty());
        assertEquals(1, tracker.snapshot(other).size());

        tracker.clear();
        assertTrue(tracker.snapshot(other).isEmpty());
    }

    private static AABB boxAt(double x) {
        return new AABB(x - 0.3, 64.0, -0.3, x + 0.3, 65.8, 0.3);
    }
}
