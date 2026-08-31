package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DeathProtectionPopRuntimeWiringContractTest {
    @Test
    void packetMixinObservesLocalTotemEventAfterVanillaAndInvalidatesSameTick() throws Exception {
        String mixin = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/mixin/ClientPacketListenerMixin.java"
        ));

        assertTrue(mixin.contains("@Inject(method = \"handleEntityEvent\", at = @At(\"TAIL\"))"),
            "event 35 must be observed after vanilla handles the local activation packet");
        assertTrue(mixin.contains("packet.getEventId() == 35"));
        assertTrue(mixin.contains("entity == minecraft.player"),
            "remote entity events must not advance the local pop generation");
        assertTrue(mixin.contains("DeathProtectionPopTracker.global().observeLocalTotemPop"));
        assertTrue(mixin.contains("SurvivalStateInvalidationReason.LOCAL_TOTEM_POP"));
    }

    @Test
    void runtimeConsumesPopTrackerForProtectionRoutingRestorationAndLifecycleReset() throws Exception {
        String runtime = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/core/MinecraftSurvivalRuntime.java"
        ));

        assertTrue(runtime.contains("DeathProtectionPopTracker.global()"));
        assertTrue(runtime.contains("popTracker.reconcile("));
        assertTrue(runtime.contains("popTracker.conservativeInventoryAfterPop("));
        assertTrue(runtime.contains("popTracker.projectedDeathProtectionAt("));
        assertTrue(runtime.contains("candidateGenerator.generate("));
        assertTrue(runtime.contains("popTracker"), "candidate generation must receive the pop tracker");
        assertTrue(runtime.contains("popTracker.generation()"));
        assertTrue(runtime.contains("popTracker.consumptionUnresolved()"));
        assertTrue(runtime.contains("popTracker.reset()"), "respawn/disconnect/runtime reset must discard stale pop state");
    }
}
