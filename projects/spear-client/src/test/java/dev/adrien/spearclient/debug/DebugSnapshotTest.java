package dev.adrien.spearclient.debug;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.adrien.spearclient.network.ServerStateTracker;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class DebugSnapshotTest {
    @Test
    void debugLinesLabelPredictionsAsSourceModel() {
        ServerStateTracker tracker = new ServerStateTracker();
        tracker.beginSequence(21L, Vec3.ZERO);
        tracker.setTargetId(7);
        tracker.setPhase("VERIFY");
        tracker.setSourceModelTelemetry("REACH", 18.0, Double.NaN, 31.5);
        tracker.onMovementPacket(new Vec3(0, 64, 9));

        DebugSnapshot snapshot = DebugSnapshot.from(
            tracker.snapshot(),
            "TargetPlayer",
            25.0
        );

        assertEquals("TargetPlayer", snapshot.targetName());
        assertEquals(7, snapshot.targetId());
        assertEquals(4.5, snapshot.baseSpearRange(), 1e-9);
        assertTrue(snapshot.lines().stream()
            .anyMatch(line -> line.contains("Predicted source-model reach: 31.50")));
        assertTrue(snapshot.lines().stream()
            .anyMatch(line -> line.contains("Predicted raw damage: n/a")));
    }
}
