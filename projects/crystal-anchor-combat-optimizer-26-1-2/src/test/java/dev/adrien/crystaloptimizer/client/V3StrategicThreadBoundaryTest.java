package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3StrategicThreadBoundaryTest {
    @Test
    void workerStrategyIsPureAndCoordinatorNeverBlocksOnWorkerFuture() throws Exception {
        Path pureBuilderPath = Path.of(
            "src/main/java/dev/adrien/crystaloptimizer/v2/strategy/StrategicDamageMapBuilder.java"
        );
        Path servicePath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicPlannerService.java"
        );
        Path coordinatorPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        );
        Path capturePath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicSnapshotCapture.java"
        );

        assertTrue(Files.exists(pureBuilderPath));
        assertTrue(Files.exists(servicePath));
        assertTrue(Files.exists(capturePath));

        String pureBuilder = Files.readString(pureBuilderPath);
        String service = Files.readString(servicePath);
        String coordinator = Files.readString(coordinatorPath);
        String capture = Files.readString(capturePath);

        assertFalse(pureBuilder.contains("net.minecraft.client.Minecraft"));
        assertFalse(pureBuilder.contains("net.minecraft.client.multiplayer.ClientLevel"));
        assertFalse(pureBuilder.contains("net.minecraft.client.player.LocalPlayer"));
        assertFalse(pureBuilder.contains("net.minecraft.client.player.AbstractClientPlayer"));
        assertFalse(pureBuilder.contains("TimingEngine"));
        assertTrue(pureBuilder.contains("snapshot.timing()"));

        assertTrue(service.contains("crystaloptimizer-strategic"));
        assertTrue(service.contains("latestToken"));
        assertTrue(service.contains("pollLatest"));
        assertTrue(capture.contains("capture("));
        assertTrue(capture.contains("TimingSnapshot"));

        assertFalse(coordinator.contains(".join()"));
        assertFalse(coordinator.contains("Future.get("));
        assertTrue(coordinator.contains("pollLatest()"));
        assertTrue(coordinator.contains("submit("));
    }
}
