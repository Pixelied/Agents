package dev.adrien.crystaloptimizer.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class V2PacketObservationArchitectureTest {
    private static final Path CLIENT_PACKET_MIXIN = Path.of(
        "src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientPacketListenerMixin.java"
    );
    private static final Path COMMON_PACKET_MIXIN = Path.of(
        "src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientCommonPacketListenerImplMixin.java"
    );
    private static final Path EVENT_BUS = Path.of(
        "src/client/java/dev/adrien/crystaloptimizer/client/v2/ClientCombatEventBus.java"
    );

    @Test
    void exact2612PacketHooksFeedTheReactiveLaneWithoutPlannerDependencies() throws IOException {
        String incoming = Files.readString(CLIENT_PACKET_MIXIN);
        String outgoing = Files.readString(COMMON_PACKET_MIXIN);

        assertTrue(incoming.contains("method = \"handleAddEntity\", at = @At(\"TAIL\")"));
        assertTrue(incoming.contains("method = \"handleRemoveEntities\", at = @At(\"HEAD\")"));
        assertTrue(incoming.contains("method = \"handleBlockUpdate\", at = @At(\"TAIL\")"));
        assertTrue(incoming.contains("method = \"handleChunkBlocksUpdate\", at = @At(\"TAIL\")"));
        assertTrue(incoming.contains("method = \"handleBlockChangedAck\", at = @At(\"TAIL\")"));
        assertTrue(incoming.contains("method = \"handleEntityEvent\", at = @At(\"TAIL\")"));
        assertTrue(outgoing.contains("ServerboundAttackPacket"));
        assertTrue(outgoing.contains("ServerboundUseItemOnPacket"));

        String combined = incoming + outgoing + Files.readString(EVENT_BUS);
        for (String forbidden : new String[] {
            "BeamPlanner",
            "CandidateGenerator",
            "TargetPredictor",
            "ClientCombatSnapshotBuilder"
        }) {
            assertFalse(combined.contains(forbidden), "hot packet path references " + forbidden);
        }
    }
}
