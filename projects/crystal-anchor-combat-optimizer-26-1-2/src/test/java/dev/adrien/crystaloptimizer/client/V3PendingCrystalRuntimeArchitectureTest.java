package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3PendingCrystalRuntimeArchitectureTest {
    @Test
    void runtimeSharesOnePendingMaskAcrossDispatchLiveViewAndContinuation() throws IOException {
        String coordinator = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));
        String liveView = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientLiveCombatView.java"
        ));
        String dispatcher = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ReactiveBurstDispatcher.java"
        ));

        assertTrue(coordinator.contains("PendingCrystalMask pendingCrystals = new PendingCrystalMask()"));
        assertTrue(coordinator.contains("result.plannedOpportunity()"),
            "sequence publishing must remain intact while continuations are hardened");
        assertTrue(coordinator.contains("consumeContinuationDependency(event)"));
        assertTrue(coordinator.contains("pending.consumedDependencies()"));

        assertTrue(liveView.contains("PendingCrystalMask pendingCrystals"));
        assertTrue(liveView.contains("pendingCrystals.isPendingRemoval"));

        assertTrue(dispatcher.contains("PendingCrystalMask pendingCrystals"));
        assertTrue(dispatcher.contains("receipt.status() == DispatchReceipt.Status.SENT"));
        assertTrue(dispatcher.contains("markPendingCrystalAttack"));
        assertTrue(dispatcher.contains("TimingTransition.CRYSTAL_ATTACK_TO_REMOVAL"));
    }
}
