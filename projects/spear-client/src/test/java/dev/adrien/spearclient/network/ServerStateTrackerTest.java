package dev.adrien.spearclient.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class ServerStateTrackerTest {
    @Test
    void correctionMarksActiveSequenceRejected() {
        ServerStateTracker tracker = new ServerStateTracker();
        tracker.beginSequence(7L, Vec3.ZERO);
        tracker.onMovementPacket(new Vec3(6, 64, 0));
        tracker.onCorrection(new Vec3(0, 64, 0));

        assertTrue(tracker.snapshot().corrected());
        assertEquals(1, tracker.snapshot().correctionCount());
        assertEquals(1, tracker.snapshot().movementPacketsSent());
        assertEquals(new Vec3(6, 64, 0), tracker.snapshot().lastRequestedPosition());
    }

    @Test
    void newSequenceClearsPriorCorrectionState() {
        ServerStateTracker tracker = new ServerStateTracker();
        tracker.beginSequence(7L, Vec3.ZERO);
        tracker.onCorrection(new Vec3(1, 2, 3));
        tracker.beginSequence(8L, new Vec3(4, 5, 6));

        assertFalse(tracker.snapshot().corrected());
        assertEquals(0, tracker.snapshot().correctionCount());
        assertEquals(8L, tracker.snapshot().sequenceId());
        assertEquals(new Vec3(4, 5, 6), tracker.snapshot().origin());
    }

    @Test
    void correctionOutsideSequenceIsIgnored() {
        ServerStateTracker tracker = new ServerStateTracker();
        tracker.onCorrection(new Vec3(1, 2, 3));

        assertFalse(tracker.snapshot().corrected());
        assertEquals(0, tracker.snapshot().correctionCount());
    }

    @Test
    void productionSharedTrackerIsStable() {
        assertSame(ServerStateTracker.shared(), ServerStateTracker.shared());
    }

    @Test
    void activeSequenceMetadataCanBeUpdated() {
        ServerStateTracker tracker = new ServerStateTracker();
        tracker.beginSequence(10L, Vec3.ZERO);
        tracker.setPhase("ATTACK");
        tracker.setTargetId(42);

        assertEquals("ATTACK", tracker.snapshot().phase());
        assertEquals(42, tracker.snapshot().targetId());
    }

    @Test
    void sourceModelTelemetryIsStoredWithoutCallingItVerified() {
        ServerStateTracker tracker = new ServerStateTracker();
        tracker.beginSequence(12L, Vec3.ZERO);
        tracker.setSourceModelTelemetry("REACH", 18.0, Double.NaN, 31.5);

        assertEquals("REACH", tracker.snapshot().kind());
        assertEquals(18.0, tracker.snapshot().expectedForwardKnownMovement(), 1e-9);
        assertTrue(Double.isNaN(tracker.snapshot().predictedRawDamage()));
        assertEquals(31.5, tracker.snapshot().predictedSourceModelReach(), 1e-9);
    }

    @Test
    void maxRequestedDeltaUsesDistanceFromSequenceOrigin() {
        ServerStateTracker tracker = new ServerStateTracker();
        tracker.beginSequence(13L, Vec3.ZERO);
        tracker.onMovementPacket(new Vec3(3, 4, 0));
        tracker.onMovementPacket(new Vec3(2, 0, 0));

        assertEquals(5.0, tracker.snapshot().maxRequestedDelta(), 1e-9);
    }

    @Test
    void terminalResultDistinguishesDoneCorrectedAndAborted() {
        ServerStateTracker done = new ServerStateTracker();
        done.beginSequence(14L, Vec3.ZERO);
        done.endSequence("DONE");
        assertEquals("done", done.snapshot().lastResult());

        ServerStateTracker corrected = new ServerStateTracker();
        corrected.beginSequence(15L, Vec3.ZERO);
        corrected.onCorrection(Vec3.ZERO);
        corrected.endSequence("FAILED");
        assertEquals("corrected", corrected.snapshot().lastResult());

        ServerStateTracker aborted = new ServerStateTracker();
        aborted.beginSequence(16L, Vec3.ZERO);
        aborted.endSequence("FAILED");
        assertEquals("aborted", aborted.snapshot().lastResult());
    }

    @Test
    void endingSequenceStopsAttributingLaterCorrections() {
        ServerStateTracker tracker = new ServerStateTracker();
        tracker.beginSequence(11L, Vec3.ZERO);
        tracker.endSequence("DONE");
        tracker.onCorrection(new Vec3(4, 5, 6));

        assertFalse(tracker.snapshot().active());
        assertFalse(tracker.snapshot().corrected());
        assertEquals("DONE", tracker.snapshot().phase());
    }
}
