package dev.adrien.crystaloptimizer.world;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientCombatSnapshotBuilderArchitectureTest {
    private static final Path SOURCE = Path.of(
        "src/client/java/dev/adrien/crystaloptimizer/client/world/ClientCombatSnapshotBuilder.java"
    );

    @Test
    void snapshotBuilderCopiesOnlyClientVisibleCombatState() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("ObservedCombatantAssembler.self"));
        assertTrue(source.contains("ObservedCombatantAssembler.target"));
        assertTrue(source.contains("entitiesForRendering()"));
        assertTrue(source.contains("entity.getId()"));
        assertTrue(source.contains("RespawnAnchorBlock.CHARGE"));
        assertTrue(source.contains("EnvironmentAttributes.RESPAWN_ANCHOR_WORKS"));
        assertTrue(source.contains("CombatRegion.of"));
        assertTrue(source.contains("InteractionTimingRecorder.model()"));

        assertFalse(source.contains("target.getInventory()"));
        assertFalse(source.contains("getAbsorptionAmount()"));
        assertFalse(source.contains("new Serverbound"));
        assertFalse(source.contains("setDeltaMovement("));
        assertFalse(source.contains("input."));
    }
}
