package dev.pixelied.survival;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrgentReevaluationContractTest {
    @Test
    void automationReevaluatesAtStartOfClientTickRatherThanLateEndPass() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/PredictiveSurvivalClient.java"
        ));

        assertTrue(source.contains("ClientTickEvents.START_CLIENT_TICK.register"));
        assertFalse(source.contains("ClientTickEvents.END_CLIENT_TICK.register"));
        assertEquals(1, occurrences(source, "engine.tick();"), "automation must still run exactly once per client tick");
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
