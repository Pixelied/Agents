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
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JumpBoostExpiryFallDamageTest {
    @Test
    void oneTickJumpBoostExpiresBeforeLandingDamageUsesSafeFallDistance() {
        StatusEffectsSnapshot effects = new StatusEffectsSnapshot(
            false,
            -1,
            Map.of("minecraft:jump_boost", new EffectInstanceSnapshot("minecraft:jump_boost", 1, 0))
        );
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            effects,
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.2, 1.4, 0.2, 0.8, 3.2, 0.8),
            new Vec3Snapshot(0.2, 1.4, 0.2),
            new Vec3Snapshot(0, -0.5, 0),
            Map.of(),
            Map.of(
                "fall_distance", "3.6",
                "safe_fall_distance", "4.0",
                "fall_damage_multiplier", "1.0",
                "base_gravity", "0.08",
                "effective_gravity", "0.08",
                "vertical_friction", "0.98",
                "horizontal_friction", "0.91",
                "fall_flying", "false",
                "suppressing_bounce", "false"
            )
        );
        WorldSnapshot.BlockSnapshot floor = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(0, 0, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );
        PredictionContext context = new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of(floor)),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );

        LandingPrediction landing = new FallLandingSolver().solve(context)
            .orElseThrow(() -> new AssertionError("falling player must land on the confirmed stone floor"));

        assertEquals(1L, landing.tick());
        assertEquals(1f, landing.rawFallDamage().max(), 0.0001f,
            "Jump Boost must be removed before landing damage when its final tick expires first");
    }
}
