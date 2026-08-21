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
        assertTrue(source.contains("InteractionTimingRecorder.instance().estimateBurst"));
        assertTrue(source.contains("self.getAbsorptionAmount()"),
            "local absorption is directly observable and must be captured exactly");

        assertFalse(source.contains("target.getInventory()"));
        assertFalse(source.contains("target.getAbsorptionAmount()"),
            "remote absorption must stay inferred from client-visible effects rather than hidden target state");
        assertFalse(source.contains("new Serverbound"));
        assertFalse(source.contains("setDeltaMovement("));
        assertFalse(source.contains("input."));
    }
}
