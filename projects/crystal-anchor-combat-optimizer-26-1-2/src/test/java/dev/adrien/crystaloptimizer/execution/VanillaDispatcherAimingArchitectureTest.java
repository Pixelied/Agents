package dev.adrien.crystaloptimizer.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaDispatcherAimingArchitectureTest {
    @Test
    void realVisibleAimGatesCrystalAndBlockInteractionsWithoutV1Scheduler() throws IOException {
        Path dispatcherPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/execution/VanillaInteractionDispatcher.java"
        );
        String source = Files.readString(dispatcherPath);

        assertTrue(source.contains("rotations.updateToward("),
            "dispatcher must drive the existing real RotationController before interactions");
        assertTrue(source.contains("return dispatch(action, rotationMode, false);"),
            "ordinary dispatch must use real configured rotation without fabricating a V1 commit state");
        assertFalse(source.contains("CommitScheduler"),
            "V2 dispatcher must not retain the superseded V1 scheduler");
        assertFalse(source.contains("CommitPhase"),
            "V2 criticality is supplied explicitly by the reactive lane");
        assertTrue(source.contains("DispatchReceipt.deferred(\"real rotation still converging\")"),
            "normal smooth aiming must defer the action until the visible rotation reaches target");

        int attackAim = source.indexOf(
            "aimAt(entity.getBoundingBox().getCenter(), mode, critical)"
        );
        int attackSend = source.indexOf("minecraft.gameMode.attack(player, entity)");
        assertTrue(attackAim >= 0 && attackAim < attackSend,
            "known-crystal attack must visibly aim before the vanilla attack call");

        int useHelper = source.indexOf("private DispatchReceipt useItemOn");
        int blockAim = source.indexOf("aimAt(hit.getLocation(), mode, critical)", useHelper);
        int useSend = source.indexOf(
            "minecraft.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit)",
            useHelper
        );
        assertTrue(useHelper >= 0 && blockAim > useHelper && blockAim < useSend,
            "all block interactions must visibly aim at their real hit location before useItemOn");
    }
}
