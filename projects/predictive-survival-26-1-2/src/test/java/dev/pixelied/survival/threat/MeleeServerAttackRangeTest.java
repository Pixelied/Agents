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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeleeServerAttackRangeTest {
    @Test
    void serverAttackPacketBufferExtendsActualPlayerMeleeReach() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            5.0,
            Map.of(
                "attack_range", "3",
                "main_hand_attack_min_range", "0",
                "main_hand_attack_max_range", "3",
                "main_hand_attack_hitbox_margin", "0"
            )
        );

        assertEquals(1, new MeleePredictor().predict(context(attacker)).size());
    }

    @Test
    void serverAttackComponentMinimumRangeRejectsTooCloseTarget() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            2.0,
            Map.of(
                "attack_range", "10",
                "main_hand_attack_min_range", "8",
                "main_hand_attack_max_range", "10",
                "main_hand_attack_hitbox_margin", "0"
            )
        );

        assertTrue(new MeleePredictor().predict(context(attacker)).isEmpty());
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

    private static WorldSnapshot.EntitySnapshot attacker(double x, Map<String, String> rangeProperties) {
        java.util.LinkedHashMap<String, String> properties = new java.util.LinkedHashMap<>();
        properties.put("melee_capable", "true");
        properties.put("melee_model", "player");
        properties.put("attack_damage", "10");
        properties.put("attack_strength", "1");
        properties.put("weapon_key", "minecraft:diamond_sword");
        properties.put("critical_possible", "false");
        properties.put("line_of_sight", "true");
        properties.put("eye_position_x", Double.toString(x));
        properties.put("eye_position_y", "1.62");
        properties.put("eye_position_z", "0.3");
        properties.putAll(rangeProperties);

        return new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            new Vec3Snapshot(x, 0.0, 0.3),
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(x - 0.3, 0.0, 0.0, x + 0.3, 1.8, 0.6),
            Map.copyOf(properties)
        );
    }
}
