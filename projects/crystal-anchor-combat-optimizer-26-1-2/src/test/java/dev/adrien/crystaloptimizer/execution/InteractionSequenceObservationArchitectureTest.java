package dev.adrien.crystaloptimizer.execution;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionSequenceObservationArchitectureTest {
    @Test
    void outgoingUseItemSequenceIsObservedWithoutPacketFabrication() throws IOException {
        Path mixin = Path.of(
            "src/client/java/dev/adrien/crystaloptimizer/client/mixin/ClientCommonPacketListenerImplMixin.java"
        );
        String source = Files.readString(mixin);

        assertTrue(source.contains("ServerboundUseItemOnPacket"));
        assertTrue(source.contains("InteractionTimingRecorder.instance().recordSend("));
        assertTrue(source.contains("getSequence()"));
        assertFalse(source.contains("new ServerboundUseItemOnPacket"));
        assertFalse(source.contains("ci.cancel()"));
    }
}
