package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RemoteDamageWindowArchitectureTest {
    @Test
    void clientCorrelatesBoundedDamageEvidenceFromObservableStateOnly() throws Exception {
        Path observerPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/intel/RemoteDamageWindowObserver.java"
        );
        assertTrue(Files.exists(observerPath));
        String observer = Files.readString(observerPath);
        String mixin = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientPacketListenerMixin.java"
        ));
        String coordinator = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));

        assertTrue(observer.contains("MAX_CANDIDATES = 16"));
        assertTrue(observer.contains("MAX_AGE_NANOS = 1_000_000_000L"));
        assertTrue(observer.contains("DamageWindowEvidence.bounded"));
        assertTrue(observer.contains("DamageWindowEvidence.exact"));
        assertTrue(mixin.contains("handleSetEntityData"));
        assertTrue(mixin.contains("onObservedTargetState"));
        assertFalse(mixin.contains("packet.packedItems()"),
            "remote evidence must come from post-vanilla observable entity state, not packet internals");
        assertTrue(coordinator.contains("postMitigationExpected()"));
        assertTrue(coordinator.contains("onExplosionCandidate"));
    }
}
