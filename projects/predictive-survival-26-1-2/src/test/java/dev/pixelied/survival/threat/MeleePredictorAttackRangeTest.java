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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MeleePredictorAttackRangeTest {
    private final MeleePredictor predictor = new MeleePredictor();

    @Test
    void serverAttackPacketBufferAllowsHitBeyondBareEntityInteractionRange() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(5.5, 0.0, 0.3),
            3.0,
            0.0,
            3.0,
            0.0
        );

        assertEquals(1, predictor.predict(context(attacker)).size());
    }

    @Test
    void mainHandMinimumAttackRangeRejectsTooCloseHit() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(3.0, 0.0, 0.3),
            10.0,
            8.0,
            10.0,
            0.0
        );

        assertTrue(predictor.predict(context(attacker)).isEmpty());
    }

    @Test
    void mainHandHitboxMarginExtendsServerAcceptedRange() {
        WorldSnapshot.EntitySnapshot attacker = attacker(
            new Vec3Snapshot(7.0, 0.0, 0.3),
            3.0,
            0.0,
            3.0,
            1.0
        );

        assertEquals(1, predictor.predict(context(attacker)).size());
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

    private static WorldSnapshot.EntitySnapshot attacker(
        Vec3Snapshot position,
        double entityRange,
        double minRange,
        double maxRange,
        double hitboxMargin
    ) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("melee_capable", "true");
        properties.put("melee_model", "player");
        properties.put("attack_damage", "10");
        properties.put("attack_strength", "1");
        properties.put("weapon_key", "minecraft:diamond_sword");
        properties.put("critical_possible", "false");
        properties.put("line_of_sight", "true");
        properties.put("attack_range", Double.toString(entityRange));
        properties.put("main_hand_attack_min_range", Double.toString(minRange));
        properties.put("main_hand_attack_max_range", Double.toString(maxRange));
        properties.put("main_hand_attack_hitbox_margin", Double.toString(hitboxMargin));
        properties.put("eye_position_x", Double.toString(position.x()));
        properties.put("eye_position_y", "1.62");
        properties.put("eye_position_z", Double.toString(position.z()));
        return new WorldSnapshot.EntitySnapshot(
            "attacker",
            "minecraft:player",
            position,
            new Vec3Snapshot(0.0, 0.0, 0.0),
            new AabbSnapshot(
                position.x() - 0.3, position.y(), position.z() - 0.3,
                position.x() + 0.3, position.y() + 1.8, position.z() + 0.3
            ),
            Map.copyOf(properties)
        );
    }
}
