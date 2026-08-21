package dev.adrien.crystaloptimizer.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class V2LethalEfficiencyArchitectureTest {
    @Test
    void damageMapBuilderAdmitsThroughSafetyAndSpendPolicyBeforePublishing() throws IOException {
        String source = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientDamageMapBuilder.java"
        ));

        assertTrue(source.contains("SelfDamageEstimate"));
        assertTrue(source.contains("LethalEfficiencyPolicy.evaluate"));
        assertTrue(source.contains("ResourceChain"));
        assertTrue(source.contains("totemTriggered()"));
    }
}
