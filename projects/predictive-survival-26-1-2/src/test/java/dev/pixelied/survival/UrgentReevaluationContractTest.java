package dev.pixelied.survival;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrgentReevaluationContractTest {
    @Test
    void automationKeepsMandatoryStartPassAndCoalescesDirtyPacketsIntoOneEndPass() throws Exception {
        String client = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/PredictiveSurvivalClient.java"
        ));
        String mixin = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/mixin/ClientPacketListenerMixin.java"
        ));

        assertTrue(client.contains("ClientTickEvents.START_CLIENT_TICK.register"));
        assertTrue(client.contains("ClientTickEvents.END_CLIENT_TICK.register"));
        assertTrue(client.contains("consumeThreatDirty()"),
            "the optional END pass must be guarded by one coalesced dirty signal");
        assertEquals(2, occurrences(client, "engine.tick();"),
            "there must be one mandatory START pass and at most one guarded END pass");

        assertTrue(mixin.contains("handleAddEntity"));
        assertTrue(mixin.contains("handleSetEntityMotion"));
        assertTrue(mixin.contains("handleEntityPositionSync"));
        assertTrue(mixin.contains("handleTeleportEntity"));
        assertTrue(mixin.contains("handleMoveEntity"));
        assertTrue(mixin.contains("handleRemoveEntities"));
        assertTrue(mixin.contains("@At(\"TAIL\")"),
            "packet handlers must finish vanilla state mutation before marking prediction dirty");
    }

    @Test
    void dirtySignalCoalescesAndConsumes() {
        ThreatDirtyTracker tracker = new ThreatDirtyTracker();
        assertEquals(false, tracker.consumeDirty());
        tracker.markDirty();
        tracker.markDirty();
        assertEquals(true, tracker.consumeDirty());
        assertEquals(false, tracker.consumeDirty());
        tracker.markDirty();
        tracker.reset();
        assertEquals(false, tracker.consumeDirty());
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
