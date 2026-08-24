package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3MotionIntegrationArchitectureTest {
    @Test
    void movementPacketsFeedBoundedHistoryAndStrategicCaptureCarriesIt() throws Exception {
        String packetMixin = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientPacketListenerMixin.java"
        ));
        String capture = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicSnapshotCapture.java"
        ));
        String coordinator = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));

        assertTrue(packetMixin.contains("TargetMotionTracker.instance().observe"));
        assertTrue(packetMixin.contains("TargetMotionTracker.instance().remove"));
        assertTrue(capture.contains("TargetMotionTracker.instance().snapshot"));
        assertTrue(coordinator.contains("TargetMotionTracker.instance().clear()"));
    }
}
