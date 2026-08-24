package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
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

class MeleeApproachPredictionTest {
    @Test
    void approachingRemoteMaceIsPredictedBeforeEnteringCurrentReach() {
        WorldSnapshot.EntitySnapshot approaching = new WorldSnapshot.EntitySnapshot(
            "attacker:approaching-mace",
            "minecraft:player",
            new Vec3Snapshot(5.0, 0, 0),
            new Vec3Snapshot(-1.5, 0, 0),
            new AabbSnapshot(5.0, 0, 0, 5.6, 1.8, 0.6),
            Map.ofEntries(
                Map.entry("melee_capable", "true"),
                Map.entry("melee_model", "player"),
                Map.entry("attack_damage_min", "0"),
                Map.entry("attack_damage_max", Float.toString(Float.MAX_VALUE)),
                Map.entry("attack_range", "3"),
                Map.entry("attack_strength_min", "0"),
                Map.entry("attack_strength_max", "1"),
                Map.entry("weapon_key", "minecraft:mace"),
                Map.entry("fall_distance_min", "0"),
                Map.entry("fall_distance_max", Float.toString(Float.MAX_VALUE)),
                Map.entry("critical_possible", "unknown")
            )
        );

        var event = new MeleePredictor().predict(context(approaching)).stream()
            .filter(candidate -> candidate.id().equals("melee:attacker:approaching-mace"))
            .findFirst()
            .orElseThrow();

        assertEquals(Confidence.POTENTIAL, event.confidence());
        assertTrue(event.impact().earliest() > 0L);
        assertTrue(event.impact().earliest() <= 2L);
        assertTrue(event.damage().flags().contains(DamageFlag.IS_MACE_SMASH));
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot attacker) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(attacker), List.of()),
            new TimingSnapshot(0, 100, 10, new dev.pixelied.survival.core.TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
