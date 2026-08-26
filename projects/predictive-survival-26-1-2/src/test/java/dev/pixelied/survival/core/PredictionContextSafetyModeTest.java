package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PredictionContextSafetyModeTest {
    @Test
    void explicitSafetyModeIsPreservedAndLegacyConstructorDefaultsBalanced() {
        PlayerSnapshot player = player();
        WorldSnapshot world = WorldSnapshot.empty();
        TimingSnapshot timing = new TimingSnapshot(0, 100, 10, new TickWindow(1, 2));
        EngineLimits limits = EngineLimits.defaults();

        PredictionContext safe = new PredictionContext(player, world, timing, limits, SafetyMode.SAFE);
        PredictionContext legacy = new PredictionContext(player, world, timing, limits);

        assertEquals(SafetyMode.SAFE, safe.safetyMode());
        assertEquals(SafetyMode.BALANCED, legacy.safetyMode());
    }

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }
}
