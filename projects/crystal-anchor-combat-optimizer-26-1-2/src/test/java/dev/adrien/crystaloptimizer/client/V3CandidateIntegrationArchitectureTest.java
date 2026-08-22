package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3CandidateIntegrationArchitectureTest {
    @Test
    void damageMapBuilderUsesSelectionPolicyInsteadOfRawMaxCandidateBreak() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/dev/adrien/crystaloptimizer/v2/strategy/StrategicDamageMapBuilder.java"
        ));
        assertFalse(source.contains("MAX_CANDIDATES"));
        assertTrue(source.contains("selectionPolicy.select("));
    }
}
