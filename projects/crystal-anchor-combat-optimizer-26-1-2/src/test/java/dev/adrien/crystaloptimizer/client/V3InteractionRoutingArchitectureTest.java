package dev.adrien.crystaloptimizer.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3InteractionRoutingArchitectureTest {
    @Test
    void liveDispatcherUsesInteractionRoutesInsteadOfHardcodedMainHand() throws IOException {
        String dispatcher = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java"
        ));

        assertTrue(dispatcher.contains("InteractionRoute"));
        assertTrue(dispatcher.contains("CrystalAttackRoutePolicy"));
        assertTrue(dispatcher.contains("InventoryCoordinator"));
        assertTrue(dispatcher.contains("InteractionHand hand"));
        assertFalse(dispatcher.contains(
            "gameMode.useItemOn(player, InteractionHand.MAIN_HAND"
        ));
    }

    @Test
    void liveRoutingCapturesWeaknessAndStrengthFromTheActualPlayer() throws IOException {
        String dispatcher = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java"
        ));

        assertTrue(dispatcher.contains("MobEffects.WEAKNESS"));
        assertTrue(dispatcher.contains("MobEffects.STRENGTH"));
        assertTrue(dispatcher.contains("CrystalAttackCapability.vanilla26_1_2()"));
    }
}
