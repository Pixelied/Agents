package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftSurvivalRuntimeAuthorityTrackingContractTest {
    @Test
    void dispatchedHotbarMutationsEnterRichEquipmentAuthorityQueueAtSendTime() throws Exception {
        String runtime = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java"
        ));

        assertFalse(
            runtime.contains("authority.sentHotbarSelection(select.hotbarIndex(), state.timing());"),
            "restoration must not use the legacy scalar-only hotbar tracker"
        );
        assertFalse(
            runtime.contains("authority.sentHotbarSelection(select.hotbarIndex(), timing);"),
            "action dispatch must not use the legacy scalar-only hotbar tracker"
        );

        Pattern restoration = Pattern.compile(
            "authority\\.sentHotbarSelection\\(\\s*"
                + "select\\.hotbarIndex\\(\\),\\s*"
                + "state\\.timing\\(\\),\\s*"
                + "state\\.inventory\\(\\),\\s*"
                + "PendingEquipmentMutation\\.Origin\\.RESTORE\\s*\\)",
            Pattern.DOTALL
        );
        assertTrue(
            restoration.matcher(runtime).find(),
            "restoration selection must preserve its original send-time authority window"
        );

        Pattern actionDispatch = Pattern.compile(
            "authority\\.sentHotbarSelection\\(\\s*"
                + "select\\.hotbarIndex\\(\\),\\s*"
                + "state\\.timing\\(\\),\\s*"
                + "state\\.inventory\\(\\),\\s*"
                + "origin\\s*\\)",
            Pattern.DOTALL
        );
        assertTrue(
            actionDispatch.matcher(runtime).find(),
            "action-driven selection must enter the rich equipment-authority queue"
        );
    }
}
