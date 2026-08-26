package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MinecraftExplosionSnapshotRulesTest {
    @Test
    void primedTntUsesSynchronizedFuseButNeverClaimsHiddenPowerIsExact() {
        Map<String, String> properties = MinecraftExplosionSnapshotRules.primedTnt(5);

        assertEquals("5", properties.get("fuse_ticks"));
        assertEquals("true", properties.get("countdown_server_synchronized"));
        assertEquals("true", properties.get("server_hidden_explosion_power"));
        assertEquals("4.0", properties.get("explosion_radius_default"));
        assertEquals("0.0", properties.get("explosion_radius_hidden_min"));
        assertEquals("128.0", properties.get("explosion_radius_hidden_max"));
        assertFalse(properties.containsKey("explosion_radius"));
    }

    @Test
    void everyTntMinecartIsRetainedForOpportunityAnalysisEvenBeforePriming() {
        Map<String, String> unprimed = MinecraftExplosionSnapshotRules.tntMinecart(false, -1);

        assertEquals("true", unprimed.get("tnt_minecart"));
        assertEquals("false", unprimed.get("tnt_minecart_primed"));
        assertEquals("4.0", unprimed.get("explosion_radius_default_min"));
        assertEquals("11.5", unprimed.get("explosion_radius_default_max"));
        assertEquals("0.0", unprimed.get("explosion_radius_hidden_min"));
        assertEquals("1088.0", unprimed.get("explosion_radius_hidden_max"));
        assertFalse(unprimed.containsKey("fuse_ticks_min"));
        assertFalse(unprimed.containsKey("fuse_ticks_max"));
    }

    @Test
    void primedMinecartFuseIsBoundedBecauseItIsNotSynchedEntityData() {
        Map<String, String> primed = MinecraftExplosionSnapshotRules.tntMinecart(true, 37);

        assertEquals("0", primed.get("fuse_ticks_min"));
        assertEquals("37", primed.get("fuse_ticks_max"));
        assertFalse(primed.containsKey("countdown_server_synchronized"));
    }

    @Test
    void synchronizedIgnitedFlagMakesCreeperRelevantBeforeSwellDirectionArrives() {
        assertTrue(MinecraftExplosionSnapshotRules.creeperRelevant(true, 0));
        assertTrue(MinecraftExplosionSnapshotRules.creeperRelevant(false, 1));
        assertFalse(MinecraftExplosionSnapshotRules.creeperRelevant(false, 0));

        Map<String, String> properties = MinecraftExplosionSnapshotRules.creeper(true, true, 0, 0f);
        assertEquals("0", properties.get("fuse_ticks_min"));
        assertEquals("28", properties.get("fuse_ticks_max"));
        assertEquals("6.0", properties.get("explosion_radius_default"));
        assertEquals("254.0", properties.get("explosion_radius_hidden_max"));
        assertEquals("true", properties.get("server_hidden_explosion_power"));
    }

    @Test
    void witherInvulnerabilityCountdownIsSynchronizedAndRadiusIsSourceExact() {
        Map<String, String> properties = MinecraftExplosionSnapshotRules.witherSpawn(19);

        assertEquals("19", properties.get("fuse_ticks"));
        assertEquals("true", properties.get("countdown_server_synchronized"));
        assertEquals("7.0", properties.get("explosion_radius"));
        assertEquals("minecraft:explosion", properties.get("source_key"));
    }
}
