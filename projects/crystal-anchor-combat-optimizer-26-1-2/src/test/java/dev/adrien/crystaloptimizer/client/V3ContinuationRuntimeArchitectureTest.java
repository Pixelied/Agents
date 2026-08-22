package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3ContinuationRuntimeArchitectureTest {
    @Test
    void runtimeUsesSharedReversibleMaskAndConsumesServerRemovalDependency() throws IOException {
        String coordinator = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));
        String liveView = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientLiveCombatView.java"
        ));

        assertTrue(coordinator.contains("PendingCrystalMask crystalMask = new PendingCrystalMask()"));
        assertTrue(coordinator.contains("TimingTransition.CRYSTAL_ATTACK_TO_REMOVAL"));
        assertTrue(coordinator.contains("crystalMask.markAttacked"));
        assertTrue(coordinator.contains("crystalMask.confirmRemoved"));
        assertTrue(coordinator.contains("Set<ContinuationDependency> remainingDependencies"));
        assertTrue(coordinator.contains("Set<ContinuationDependency> consumedDependencies"));
        assertTrue(coordinator.contains("pending.consume(event)"));
        assertTrue(liveView.contains("PendingCrystalMask crystalMask"));
        assertTrue(liveView.contains("crystalMask.isPendingRemoval"));
    }
}
