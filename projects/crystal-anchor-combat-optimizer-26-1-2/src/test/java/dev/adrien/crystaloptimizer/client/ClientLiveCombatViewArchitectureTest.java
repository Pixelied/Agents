package dev.adrien.crystaloptimizer.client;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ClientLiveCombatViewArchitectureTest {
    @Test
    void liveViewDoesNotBuildSnapshotsOrInvokePlanning() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientLiveCombatView.java"
        ));
        for (String forbidden : new String[] {
            "CombatSnapshot",
            "ClientCombatSnapshotBuilder",
            "BeamPlanner",
            "CandidateGenerator",
            "TargetPredictor"
        }) {
            assertFalse(source.contains(forbidden), "live view references " + forbidden);
        }
    }
}
