package dev.pixelied.survival.core;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FallingBlockDamageSnapshotTest {
    @Test
    void runtimePropertiesCarryVanillaFallingBlockDamageState() {
        Map<String, String> properties = new FallingBlockDamageSnapshot(
            true,
            40,
            2f,
            7.5,
            "minecraft:falling_anvil"
        ).properties();

        assertEquals("true", properties.get("hurt_entities"));
        assertEquals("40", properties.get("fall_damage_max"));
        assertEquals("2.0", properties.get("fall_damage_per_distance"));
        assertEquals("7.5", properties.get("fall_distance"));
        assertEquals("minecraft:falling_anvil", properties.get("damage_source"));
    }
}
