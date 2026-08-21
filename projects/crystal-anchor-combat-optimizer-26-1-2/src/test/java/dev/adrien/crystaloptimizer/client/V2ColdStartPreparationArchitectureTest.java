package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class V2ColdStartPreparationArchitectureTest {
    @Test
    void damageMapPublishesAdmittedCompletePreparationSequences() throws Exception {
        String builder = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageMapBuilder.java"
        ));
        String scanner = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientStrategicScanner.java"
        ));

        assertTrue(builder.contains("StrategicPreparationPlanner"));
        assertTrue(builder.contains("preparation.planSequences(state, config)"));
        assertTrue(builder.contains("sequence.terminalExplosion()"));
        assertTrue(builder.contains("sequence.resources()"));
        assertTrue(builder.contains("new FixedActionSequence(actions)"));
        assertTrue(builder.contains("addOpportunity("),
            "preparation must go through normal target/self damage admission");

        assertTrue(scanner.contains("ApprovalSlot.PREPARE"));
        assertTrue(scanner.contains("opportunity.id().startsWith(\"prepare:\")"));
        assertTrue(scanner.contains("!opportunity.id().startsWith(\"prepare:\")"),
            "setup must remain separate from ordinary PRESSURE approvals");
    }
}
