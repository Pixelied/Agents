package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class V2ColdStartPreparationArchitectureTest {
    @Test
    void damageMapPublishesPreparationSequenceAndScannerExposesPrepareApproval() throws Exception {
        String builder = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageMapBuilder.java"
        ));
        String scanner = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java"
        ));

        assertTrue(builder.contains("StrategicPreparationPlanner"));
        assertTrue(builder.contains("preparation.plan(state, config)"));
        assertTrue(builder.contains("\"prepare:\""));
        assertTrue(builder.contains("new FixedActionSequence(actions)"));

        assertTrue(scanner.contains("ApprovalSlot.PREPARE"));
        assertTrue(scanner.contains("opportunity.id().startsWith(\"prepare:\")"));
        assertTrue(scanner.contains("!opportunity.id().startsWith(\"prepare:\")"),
            "zero-damage setup must not masquerade as PRESSURE");
    }
}
