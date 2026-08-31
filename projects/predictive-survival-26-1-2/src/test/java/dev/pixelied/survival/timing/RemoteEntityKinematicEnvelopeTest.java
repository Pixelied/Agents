package dev.pixelied.survival.timing;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteEntityKinematicEnvelopeTest {
    @Test
    void observationAgeComesFromRttAndJitterInsteadOfFixedOneTick() {
        RemoteEntityKinematicEnvelope tracker = new RemoteEntityKinematicEnvelope(8, 8);
        TimingSnapshot timing = new TimingSnapshot(100, 200, 25, new TickWindow(101, 104));

        RemoteEntityKinematicEnvelope.Snapshot snapshot = tracker.observe(
            "42",
            "entity-uuid-a",
            100,
            vec(10, 64, 10),
            vec(1, 0, 0),
            timing,
            false
        );

        assertEquals(new TickWindow(1, 4), snapshot.observationAgeTicks());
        assertFalse(snapshot.observationAgeTicks().equals(new TickWindow(1, 1)));
    }

    @Test
    void sameLogicalTickReplacesSampleInsteadOfGrowingHistory() {
        RemoteEntityKinematicEnvelope tracker = new RemoteEntityKinematicEnvelope(8, 8);
        TimingSnapshot timing = new TimingSnapshot(10, 100, 10, new TickWindow(11, 13));

        tracker.observe("7", "uuid", 10, vec(0, 0, 0), vec(1, 0, 0), timing, false);
        RemoteEntityKinematicEnvelope.Snapshot sameTick = tracker.observe(
            "7", "uuid", 10, vec(0.25, 0, 0), vec(1, 0, 0), timing, false
        );
        RemoteEntityKinematicEnvelope.Snapshot nextTick = tracker.observe(
            "7", "uuid", 11, vec(1.25, 0, 0), vec(1, 0, 0), timing, false
        );

        assertEquals(1, sameTick.history().size());
        assertEquals(vec(0.25, 0, 0), sameTick.history().getFirst().position());
        assertEquals(2, nextTick.history().size());
    }

    @Test
    void teleportAndEntityIdReuseResetOldKinematicHistory() {
        RemoteEntityKinematicEnvelope tracker = new RemoteEntityKinematicEnvelope(8, 8);
        TimingSnapshot timing = new TimingSnapshot(20, 100, 10, new TickWindow(21, 23));

        tracker.observe("9", "uuid-a", 20, vec(0, 0, 0), vec(1, 0, 0), timing, false);
        tracker.observe("9", "uuid-a", 21, vec(1, 0, 0), vec(1, 0, 0), timing, false);
        RemoteEntityKinematicEnvelope.Snapshot teleported = tracker.observe(
            "9", "uuid-a", 22, vec(30, 5, 30), vec(0, 0, 0), timing, true
        );
        RemoteEntityKinematicEnvelope.Snapshot reusedId = tracker.observe(
            "9", "uuid-b", 23, vec(-4, 2, 8), vec(0, 0, 0), timing, false
        );

        assertTrue(teleported.resetBoundary());
        assertEquals(1, teleported.history().size());
        assertEquals(vec(30, 5, 30), teleported.history().getFirst().position());
        assertTrue(reusedId.resetBoundary());
        assertEquals(1, reusedId.history().size());
        assertEquals(vec(-4, 2, 8), reusedId.history().getFirst().position());
    }

    @Test
    void historyAndTrackedEntityCountsStayHardBounded() {
        RemoteEntityKinematicEnvelope tracker = new RemoteEntityKinematicEnvelope(3, 2);
        TimingSnapshot timing = new TimingSnapshot(0, 100, 10, new TickWindow(1, 3));

        RemoteEntityKinematicEnvelope.Snapshot latest = null;
        for (int tick = 0; tick < 10; tick++) {
            latest = tracker.observe(
                "1", "uuid-1", tick, vec(tick, 0, 0), vec(1, 0, 0), timing, false
            );
        }
        tracker.observe("2", "uuid-2", 10, vec(0, 0, 0), vec(0, 0, 0), timing, false);
        tracker.observe("3", "uuid-3", 10, vec(0, 0, 0), vec(0, 0, 0), timing, false);

        assertEquals(3, latest.history().size());
        assertEquals(7L, latest.history().getFirst().logicalTick());
        assertEquals(2, tracker.trackedEntityCount());
    }

    @Test
    void resetClearsEveryTrackedEntity() {
        RemoteEntityKinematicEnvelope tracker = new RemoteEntityKinematicEnvelope(3, 2);
        TimingSnapshot timing = new TimingSnapshot(0, 100, 10, new TickWindow(1, 3));
        tracker.observe("1", "uuid-1", 0, vec(0, 0, 0), vec(0, 0, 0), timing, false);

        tracker.reset();

        assertEquals(0, tracker.trackedEntityCount());
    }

    private static Vec3Snapshot vec(double x, double y, double z) {
        return new Vec3Snapshot(x, y, z);
    }
}
