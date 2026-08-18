package dev.adrien.crystaloptimizer.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionArchitectureTest {
    @Test
    void clientExecutionNeverWritesMovementOrConstructsFakeInteractionPackets() throws IOException {
        Path root = Path.of("src/client/java/dev/adrien/crystaloptimizer/client/execution");
        String source;
        try (var paths = Files.walk(root)) {
            source = paths
                .filter(path -> path.toString().endsWith(".java"))
                .map(ExecutionArchitectureTest::readUnchecked)
                .reduce("", (left, right) -> left + "\n" + right);
        }

        for (String forbidden : List.of(
            "setPos(",
            "setDeltaMovement(",
            "player.input",
            "forwardImpulse",
            "leftImpulse",
            "options.keyUp",
            "options.keyDown",
            "options.keyLeft",
            "options.keyRight",
            "new ServerboundUseItemOnPacket",
            "new ServerboundInteractPacket"
        )) {
            assertFalse(source.contains(forbidden), "execution source contains forbidden primitive: " + forbidden);
        }

        assertTrue(source.contains("gameMode.attack("));
        assertTrue(source.contains("gameMode.useItemOn("));
        assertTrue(source.contains("getInventory().setSelectedSlot("));
        assertTrue(source.contains("player.setYRot("));
        assertTrue(source.contains("player.setXRot("));
    }

    private static String readUnchecked(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
