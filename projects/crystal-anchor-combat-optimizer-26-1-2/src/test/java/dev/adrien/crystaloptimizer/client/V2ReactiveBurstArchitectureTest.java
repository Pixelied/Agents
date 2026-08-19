package dev.adrien.crystaloptimizer.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class V2ReactiveBurstArchitectureTest {
    @Test
    void reactiveBurstUsesOrderedVanillaActionsAndRealRotations() throws IOException {
        String vanilla = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java"
        ));
        String burst = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ReactiveBurstDispatcher.java"
        ));
        String outgoing = Files.readString(Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientCommonPacketListenerImplMixin.java"
        ));

        assertTrue(vanilla.contains("dispatch(CombatAction action, RotationMode mode, boolean critical)"));
        assertTrue(vanilla.contains("minecraft.gameMode.attack(player, entity)"));
        assertTrue(vanilla.contains("minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)"));
        assertTrue(vanilla.contains("rotations.updateToward(target, mode, critical)"));
        assertTrue(burst.contains("for (int index = 0; index < decision.actions().size(); index++)"));
        assertTrue(burst.contains("receipt.status() != DispatchReceipt.Status.SENT"));
        assertTrue(outgoing.contains("ServerboundAttackPacket"));
        assertFalse(vanilla.contains("new EndCrystal"));
        assertFalse(burst.contains("new EndCrystal"));
    }
}
