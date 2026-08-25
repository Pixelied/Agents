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

import static org.junit.jupiter.api.Assertions.assertTrue;

class CrystalSupportEndpointOcclusionTest {
    @Test
    void crystalOwnSupportDoesNotOccludeExplosionOnlyBecauseRayEndsOnTopFace() {
        WorldSnapshot.EntitySnapshot crystal = new WorldSnapshot.EntitySnapshot(
            "crystal:support-face",
            "minecraft:end_crystal",
            new Vec3Snapshot(2.5, 0.0, 0.5),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(1.5, 0.0, -0.5, 3.5, 2.0, 1.5),
            Map.of(
                "explosion_radius", "6.0",
                "triggerable", "true",
                "source_key", "minecraft:explosion",
                "scales_with_difficulty", "true"
            )
        );
        WorldSnapshot.BlockSnapshot support = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(2.5, -0.5, 0.5),
            "minecraft:obsidian",
            true,
            Map.of("full_collision_cube", "true")
        );

        float rawMax = new ExplosionPredictor().predict(context(crystal, support)).stream()
            .filter(event -> event.id().equals("explosion:crystal:support-face"))
            .findFirst()
            .orElseThrow()
            .damage().rawDamage().max();

        assertTrue(rawMax > 20f, "a close end crystal must not become harmless because its ray endpoint touches its own support");
    }

    private static PredictionContext context(
        WorldSnapshot.EntitySnapshot crystal,
        WorldSnapshot.BlockSnapshot support
    ) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(crystal), List.of(support)),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
