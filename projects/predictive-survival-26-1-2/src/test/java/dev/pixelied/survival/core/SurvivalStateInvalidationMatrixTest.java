package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void entityEventMatrixIncludesTotemPopAndTntMinecartPrime() throws Exception {
        String mixin = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/mixin/ClientPacketListenerMixin.java"
        ));

        assertTrue(mixin.contains("packet.getEventId() == 35"));
        assertTrue(mixin.contains("SurvivalStateInvalidationReason.LOCAL_TOTEM_POP"));
        assertTrue(mixin.contains("packet.getEventId() == 10"),
            "26.1.2 TNT minecart priming must invalidate immediately on entity event 10");
        assertTrue(mixin.contains("entity instanceof MinecartTNT"),
            "event 10 must stay narrow to the vanilla TNT minecart transition");
        assertTrue(mixin.contains("SurvivalStateInvalidationReason.TNT_MINECART_PRIMED"));
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

    @Test
    void attributeFilterUsesOnlyClientSyncableSurvivalEvidence() {
        assertTrue(SurvivalStateInvalidationReason.isSurvivalRelevantAttribute("minecraft:armor"));
        assertTrue(SurvivalStateInvalidationReason.isSurvivalRelevantAttribute("minecraft:gravity"));
        assertTrue(SurvivalStateInvalidationReason.isSurvivalRelevantAttribute("minecraft:entity_interaction_range"));
        assertFalse(SurvivalStateInvalidationReason.isSurvivalRelevantAttribute("minecraft:attack_damage"),
            "remote player attack damage is intentionally hidden/not client-syncable in the 26.1.2 plan");
        assertFalse(SurvivalStateInvalidationReason.isSurvivalRelevantAttribute("minecraft:luck"));
    }

    private static void assertHook(String mixin, String handler) {
        assertTrue(mixin.contains("method = \"" + handler + "\""),
            () -> "missing same-tick invalidation hook for " + handler);
        assertTrue(mixin.contains("@At(\"TAIL\")"),
            "urgent packet invalidation must happen after vanilla applies packet state");
    }
}
