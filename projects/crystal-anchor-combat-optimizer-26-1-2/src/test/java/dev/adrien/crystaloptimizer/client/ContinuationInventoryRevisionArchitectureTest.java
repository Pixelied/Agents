package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class ContinuationInventoryRevisionArchitectureTest {
    @Test
    void pendingContinuationCapturesAndReusesPostBurstInventoryRevision() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatCoordinator.java"
        ));

        assertTrue(source.contains("long acceptedInventoryRevision"),
            "pending continuation must own the exact inventory revision accepted after its burst");
        assertTrue(source.contains("liveView.inventoryRevision()"),
            "continuation creation must capture the live post-burst inventory revision");
        assertTrue(source.contains("pending.acceptedInventoryRevision()"),
            "continuation resume must reuse the captured revision rather than the original approval revision");
        assertTrue(source.contains("arbiter.evaluateFromContinuation("),
            "unreserved continuations must use continuation-aware freshness checks");

        int reservedCall = source.indexOf("arbiter.evaluateContinuation(");
        int acceptedRevision = source.indexOf("pending.acceptedInventoryRevision()", reservedCall);
        int callEnd = source.indexOf(");", reservedCall);
        assertTrue(reservedCall >= 0 && acceptedRevision > reservedCall && acceptedRevision < callEnd,
            "reserved continuations must pass the captured inventory revision into arbitration");
    }
}
