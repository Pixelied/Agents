package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ClientStrategicSnapshotCaptureEfficiencyTest {
    @Test
    void strategicCaptureScansTheClientWorldOnlyOnceForAllShortlistedTargets() throws Exception {
        String capture = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicSnapshotCapture.java"
        ));
        String builder = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/world/ClientCombatSnapshotBuilder.java"
        ));

        assertTrue(capture.contains("combatSnapshots.build(validTargets)"),
            "strategic capture must submit the whole bounded target shortlist to one snapshot build");
        assertFalse(capture.contains("combatSnapshots.build(target)"),
            "strategic capture must not rescan overlapping world geometry once per target");
        assertFalse(capture.contains("ArrayList<CombatSnapshot> captured"),
            "multi-target capture should not materialize N duplicate combat snapshots just to merge them");

        assertTrue(builder.contains("build(List<? extends AbstractClientPlayer> targets)"),
            "client snapshot builder must expose one-pass multi-target capture");
        assertTrue(builder.contains("for (AbstractClientPlayer target : validTargets)"),
            "one-pass capture must expand a single bounded scan region over every shortlisted target");
    }
}
