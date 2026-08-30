package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Contract for same-tick packet families that can move a survival deadline or mitigation state. */
class SurvivalStateInvalidationMatrixTest {
    @Test
    void urgentPacketFamiliesAreHookedAfterVanillaStateMutation() throws Exception {
        String mixin = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/mixin/ClientPacketListenerMixin.java"
        ));

        assertHook(mixin, "handleDamageEvent");
        assertHook(mixin, "handleEntityEvent");
        assertHook(mixin, "handleSetEntityData");
        assertHook(mixin, "handleRemoveMobEffect");
        assertHook(mixin, "handleUpdateAttributes");
        assertHook(mixin, "handleInitializeBorder");
        assertHook(mixin, "handleSetBorderCenter");
        assertHook(mixin, "handleSetBorderSize");
        assertHook(mixin, "handleSetBorderLerpSize");
    }

    @Test
    void dirtyTrackingCarriesTypedReasonsInsteadOfOnlyABoolean() throws Exception {
        String tracker = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/ThreatDirtyTracker.java"
        ));
        String client = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/PredictiveSurvivalClient.java"
        ));

        assertTrue(tracker.contains("SurvivalStateInvalidationReason"),
            "dirty coalescing must retain typed invalidation evidence for diagnostics and filtering");
        assertTrue(tracker.contains("consumeReasons"),
            "the tracker must expose the coalesced reasons, not discard them into one boolean");
        assertTrue(client.contains("markThreatDirty(SurvivalStateInvalidationReason"),
            "packet hooks must name why the optional END pass is required");
    }

    private static void assertHook(String mixin, String handler) {
        assertTrue(mixin.contains("method = \"" + handler + "\""),
            () -> "missing same-tick invalidation hook for " + handler);
        assertTrue(mixin.contains("@At(\"TAIL\")"),
            "urgent packet invalidation must happen after vanilla applies packet state");
    }
}
