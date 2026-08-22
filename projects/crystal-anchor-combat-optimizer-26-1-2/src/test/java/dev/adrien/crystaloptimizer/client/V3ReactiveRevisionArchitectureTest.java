package dev.adrien.crystaloptimizer.client;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

final class V3ReactiveRevisionArchitectureTest {
    @Test
    void liveMovementAndInventoryPathsPublishRevisionedEvents() throws Exception {
        Path packetMixinPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientPacketListenerMixin.java"
        );
        Path inventoryMixinPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/mixin/LocalInventoryMixin.java"
        );
        Path trackerPath = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientRevisionTracker.java"
        );
        Path mixinConfigPath = Path.of(
            "src/client/resources/crystaloptimizer.client.mixins.json"
        );

        String packets = Files.readString(packetMixinPath);
        String tracker = Files.readString(trackerPath);
        String mixins = Files.readString(mixinConfigPath);

        assertTrue(packets.contains("handleMoveEntity"));
        assertTrue(packets.contains("handleEntityPositionSync"));
        assertTrue(packets.contains("handleTeleportEntity"));
        assertTrue(packets.contains("markTargetMovement"));
        assertTrue(packets.contains("new CombatEvent.TargetMoved"));

        assertTrue(Files.exists(inventoryMixinPath));
        String inventory = Files.readString(inventoryMixinPath);
        assertTrue(inventory.contains("markInventoryMutation"));
        assertTrue(inventory.contains("new CombatEvent.InventoryChanged"));
        assertTrue(inventory.contains("Minecraft.getInstance().player"));

        assertTrue(tracker.contains("long markTargetMovement"));
        assertTrue(tracker.contains("long markInventoryMutation"));
        assertTrue(mixins.contains("LocalInventoryMixin"));
    }
}
