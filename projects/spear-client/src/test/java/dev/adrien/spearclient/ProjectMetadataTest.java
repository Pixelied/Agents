package dev.adrien.spearclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProjectMetadataTest {
    @Test
    void fabricMetadataIsClientOnlyAndPinnedTo2612() throws Exception {
        String json = Files.readString(Path.of("src/main/resources/fabric.mod.json"));
        assertTrue(json.contains("\"environment\": \"client\""));
        assertTrue(json.contains("\"minecraft\": \"26.1.2\""));
        assertTrue(json.contains("dev.adrien.spearclient.SpearClient"));
        assertEquals("25", System.getProperty("java.specification.version"));
    }
}
