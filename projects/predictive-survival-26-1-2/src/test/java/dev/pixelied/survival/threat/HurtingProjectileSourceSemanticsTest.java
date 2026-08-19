package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HurtingProjectileSourceSemanticsTest {
    private static final String DIRECT_ID = "projectile:hurting:1:direct";
    private final ProjectilePredictor predictor = new ProjectilePredictor();

    @Test
    void attributedFireballDirectHitIsFireAndProjectileDamage() {
        ThreatEvent event = direct("minecraft:fireball", Map.of(
            "raw_damage", "6",
            "source_key", "minecraft:fireball",
            "acceleration_power", "0"
        ));

        assertEquals("minecraft:fireball", event.damage().sourceKey());
        assertTrue(event.damage().has(DamageFlag.IS_FIRE));
        assertTrue(event.damage().has(DamageFlag.IS_PROJECTILE));
        assertFalse(event.damage().has(DamageFlag.BYPASSES_ARMOR));
        assertFalse(event.damage().has(DamageFlag.BYPASSES_SHIELD));
        assertTrue(event.blockable());
    }

    @Test
    void unattributedFireballDirectHitKeepsFireProjectileTags() {
        ThreatEvent event = direct("minecraft:fireball", Map.of(
            "raw_damage", "6",
            "source_key", "minecraft:unattributed_fireball",
            "acceleration_power", "0"
        ));

        assertEquals("minecraft:unattributed_fireball", event.damage().sourceKey());
        assertTrue(event.damage().has(DamageFlag.IS_FIRE));
        assertTrue(event.damage().has(DamageFlag.IS_PROJECTILE));
        assertTrue(event.blockable());
    }

    @Test
    void attributedWitherSkullDirectHitUsesProjectileDamage() {
        ThreatEvent event = direct("minecraft:wither_skull", Map.of(
            "raw_damage", "8",
            "source_key", "minecraft:wither_skull",
            "acceleration_power", "0"
        ));

        assertEquals("minecraft:wither_skull", event.damage().sourceKey());
        assertEquals(8f, event.damage().rawDamage().min(), 0.0001f);
        assertEquals(8f, event.damage().rawDamage().max(), 0.0001f);
        assertTrue(event.damage().has(DamageFlag.IS_PROJECTILE));
        assertFalse(event.damage().has(DamageFlag.IS_FIRE));
        assertFalse(event.damage().has(DamageFlag.BYPASSES_ARMOR));
        assertFalse(event.damage().has(DamageFlag.BYPASSES_SHIELD));
        assertTrue(event.blockable());
    }

    @Test
    void opaqueOrOwnerlessWitherSkullMagicCaseFailsClosed() {
        ThreatEvent event = direct("minecraft:wither_skull", Map.of(
            "raw_damage_min", "5",
            "raw_damage_max", "8",
            "source_key", "minecraft:magic",
            "acceleration_power", "0"
        ));

        assertEquals("minecraft:magic", event.damage().sourceKey());
        assertEquals(5f, event.damage().rawDamage().min(), 0.0001f);
        assertEquals(8f, event.damage().rawDamage().max(), 0.0001f);
        assertTrue(event.damage().has(DamageFlag.BYPASSES_ARMOR));
        assertTrue(event.damage().has(DamageFlag.BYPASSES_SHIELD));
        assertFalse(event.damage().has(DamageFlag.IS_PROJECTILE));
        assertFalse(event.blockable());
    }

    @Test
    void smallFireballAcceptedHitCreatesFiveDependentBurnPulses() {
        List<ThreatEvent> events = predict(
            "minecraft:small_fireball",
            Map.of("raw_damage", "5", "source_key", "minecraft:fireball", "acceleration_power", "0"),
            DifficultySnapshot.NORMAL,
            128
        );
        ThreatEvent direct = events.stream().filter(event -> event.id().equals(DIRECT_ID)).findFirst().orElseThrow();
        List<ThreatEvent> burns = events.stream().filter(event -> event.id().contains(":on_fire:")).toList();

        assertEquals(5, burns.size());
        for (ThreatEvent burn : burns) {
            assertEquals("minecraft:on_fire", burn.damage().sourceKey());
            assertEquals(1f, burn.damage().rawDamage().max(), 0.0001f);
            assertTrue(burn.damage().has(DamageFlag.IS_FIRE));
            assertTrue(burn.damage().has(DamageFlag.BYPASSES_ARMOR));
            assertTrue(burn.damage().has(DamageFlag.BYPASSES_SHIELD));
            assertFalse(burn.blockable());
            assertEquals(DIRECT_ID, burn.requiresAcceptedEventId().orElseThrow());
            assertTrue(burn.impact().earliest() >= direct.impact().earliest());
        }
    }

    @Test
    void witherSkullAcceptedHitCreatesDifficultyDependentWitherIiPulses() {
        Map<String, String> properties = Map.of(
            "raw_damage", "8",
            "source_key", "minecraft:wither_skull",
            "acceleration_power", "0"
        );

        List<ThreatEvent> normal = predict("minecraft:wither_skull", properties, DifficultySnapshot.NORMAL, 256);
        List<ThreatEvent> hard = predict("minecraft:wither_skull", properties, DifficultySnapshot.HARD, 256);
        List<ThreatEvent> easy = predict("minecraft:wither_skull", properties, DifficultySnapshot.EASY, 256);

        List<ThreatEvent> normalWither = witherPulses(normal);
        List<ThreatEvent> hardWither = witherPulses(hard);
        List<ThreatEvent> easyWither = witherPulses(easy);
        assertEquals(10, normalWither.size());
        assertEquals(13, hardWither.size());
        assertTrue(easyWither.isEmpty());

        for (ThreatEvent wither : hardWither) {
            assertEquals("minecraft:wither", wither.damage().sourceKey());
            assertEquals(1f, wither.damage().rawDamage().max(), 0.0001f);
            assertTrue(wither.damage().has(DamageFlag.BYPASSES_ARMOR));
            assertTrue(wither.damage().has(DamageFlag.BYPASSES_SHIELD));
            assertFalse(wither.blockable());
            assertEquals(DIRECT_ID, wither.requiresAcceptedEventId().orElseThrow());
        }
    }

    private ThreatEvent direct(String type, Map<String, String> properties) {
        return predict(type, properties, DifficultySnapshot.NORMAL, 128).stream()
            .filter(event -> event.id().equals(DIRECT_ID))
            .findFirst()
            .orElseThrow();
    }

    private List<ThreatEvent> predict(
        String type,
        Map<String, String> properties,
        DifficultySnapshot difficulty,
        int decisionHistory
    ) {
        WorldSnapshot.EntitySnapshot projectile = new WorldSnapshot.EntitySnapshot(
            "hurting:1",
            type,
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.5, 0.5, -0.2, 0.5, 1.5, 0.8),
            properties
        );
        return predictor.predict(context(projectile, difficulty, decisionHistory));
    }

    private static List<ThreatEvent> witherPulses(List<ThreatEvent> events) {
        return events.stream().filter(event -> event.id().contains(":wither_skull_wither:")).toList();
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot entity,
        DifficultySnapshot difficulty,
        int decisionHistory
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, difficulty,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(entity), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            new EngineLimits(128, 32, 80, decisionHistory)
        );
    }
}
