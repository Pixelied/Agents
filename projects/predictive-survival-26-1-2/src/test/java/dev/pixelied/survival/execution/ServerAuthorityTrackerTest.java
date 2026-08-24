package dev.pixelied.survival.execution;

import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.planner.SurvivalAction;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerAuthorityTrackerTest {
    @Test
    void heldSlotCannotConfirmBeforeLatestPacketProcessingTick() {
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(1);
        TimingSnapshot timing = new TimingSnapshot(100, 100, 10, new TickWindow(102, 104));

        tracker.sentHotbarSelection(5, timing);

        assertEquals(1, tracker.confirmedSelectedSlot(5, 103));
        assertEquals(5, tracker.confirmedSelectedSlot(5, 104));
    }

    @Test
    void heldSlotDoesNotAdvanceWhenLocalSelectionWasContradicted() {
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(1);
        TimingSnapshot timing = new TimingSnapshot(100, 100, 10, new TickWindow(102, 104));

        tracker.sentHotbarSelection(5, timing);

        assertEquals(1, tracker.confirmedSelectedSlot(2, 104));
    }

    @Test
    void shieldWarmupStartsAtConservativeServerProcessingTick() {
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(0);
        TimingSnapshot timing = new TimingSnapshot(100, 100, 10, new TickWindow(102, 104));

        tracker.sentUseItem(SurvivalAction.Hand.OFF_HAND, timing);

        assertEquals(0, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 103));
        assertEquals(0, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 104));
        assertEquals(4, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 108));
        assertEquals(5, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 109));
    }

    @Test
    void mismatchedUseHandNeverAccumulatesConfirmedWarmup() {
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(0);
        tracker.sentUseItem(
            SurvivalAction.Hand.OFF_HAND,
            new TimingSnapshot(10, 50, 0, new TickWindow(11, 11))
        );

        assertEquals(0, tracker.confirmedUseTicks(true, SurvivalAction.Hand.MAIN_HAND, 20));
    }


    @Test
    void endedUseSessionCannotBeReusedByLaterUseOfSameHand() {
        ServerAuthorityTracker tracker = new ServerAuthorityTracker(0);
        tracker.sentUseItem(
            SurvivalAction.Hand.OFF_HAND,
            new TimingSnapshot(100, 50, 0, new TickWindow(104, 104))
        );

        assertEquals(5, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 109));
        assertEquals(0, tracker.confirmedUseTicks(false, null, 120));
        assertEquals(0, tracker.confirmedUseTicks(true, SurvivalAction.Hand.OFF_HAND, 500),
            "a new local use must never inherit warmup from a completed old server-use session");
    }

    @Test
    void defaultShieldAngleAcceptsFrontAndRejectsBehind() {
        Vec3Snapshot player = new Vec3Snapshot(0, 64, 0);

        assertTrue(ServerAuthorityTracker.withinHorizontalBlockAngle(player, 0f, new Vec3Snapshot(0, 64, 5), 90f));
        assertFalse(ServerAuthorityTracker.withinHorizontalBlockAngle(player, 0f, new Vec3Snapshot(0, 64, -5), 90f));
    }
}
