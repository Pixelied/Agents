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
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MeleePredictorSpecialSourceKeyTest {
    private final MeleePredictor predictor = new MeleePredictor();

    @Test
    void liveStyleGenericPlayerSourceDoesNotHideMaceSmashSource() {
        Map<String, String> properties = baseProperties("minecraft:mace");
        properties.put("attack_damage", "6");
        properties.put("fall_distance", "20");
        properties.put("source_key", "minecraft:player_attack");

        ThreatEvent threat = only(properties);

        assertEquals("minecraft:mace_smash", threat.damage().sourceKey());
    }

    @Test
    void liveStyleGenericPlayerSourceDoesNotHideSpearSource() {
        Map<String, String> properties = baseProperties("minecraft:netherite_spear");
        properties.put("source_key", "minecraft:player_attack");
        properties.put("spear_base_mob_damage", "1");
        properties.put("spear_damage_multiplier", "1.2");
        properties.put("spear_damage_max_use_ticks", "102");
        properties.put("spear_damage_min_speed", "0");
        properties.put("spear_damage_min_relative_speed", "4.6");
        properties.put("spear_ticks_used", "20");
        properties.put("spear_attacker_speed_projection", "12");
        properties.put("spear_target_speed_projection", "0");

        ThreatEvent threat = only(properties);

        assertEquals("minecraft:spear", threat.damage().sourceKey());
    }

    private ThreatEvent only(Map<String, String> properties) {
        WorldSnapshot.EntitySnapshot attacker = new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            new Vec3Snapshot(2.0, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(1.7, 0.0, 0.0, 2.3, 1.8, 0.6),
            Map.copyOf(properties)
        );
        List<ThreatEvent> threats = predictor.predict(context(attacker));
        assertEquals(1, threats.size());
        return threats.getFirst();
    }

    private static Map<String, String> baseProperties(String weaponKey) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("melee_capable", "true");
        properties.put("melee_model", "player");
        properties.put("attack_strength", "1");
        properties.put("weapon_key", weaponKey);
        properties.put("critical_possible", "false");
        properties.put("line_of_sight", "true");
        properties.put("attack_range", "3");
        properties.put("scales_with_difficulty", "false");
        return properties;
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot attacker) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(attacker), List.of()),
            new TimingSnapshot(0, 100d, 10d, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
