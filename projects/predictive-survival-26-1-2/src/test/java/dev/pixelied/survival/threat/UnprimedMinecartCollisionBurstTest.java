package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
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
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UnprimedMinecartCollisionBurstTest {
    @Test
    void fastUnprimedMinecartProjectedIntoWallProducesImmediateBoundedExplosion() {
        WorldSnapshot.EntitySnapshot minecart = new WorldSnapshot.EntitySnapshot(
            "minecart:crash",
            "minecraft:tnt_minecart",
            new Vec3Snapshot(3.5d, 0.5d, 0.5d),
            new Vec3Snapshot(0.2d, 0d, 0d),
            new AabbSnapshot(3d, 0d, 0d, 4d, 1d, 1d),
            Map.of()
        );
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(4.5d, 0.5d, 0.5d),
            "minecraft:obsidian",
            true,
            Map.of("full_collision_cube", "true")
        );

        ThreatEvent event = new ExplosionPredictor().predict(context(List.of(minecart), List.of(wall))).stream()
            .filter(candidate -> candidate.id().equals("burst:minecart-collision:minecart:crash"))
            .findFirst()
            .orElseThrow();

        assertEquals(new TickWindow(0, 1), event.impact());
        assertEquals(Confidence.BOUNDED, event.confidence());
        assertTrue(event.damage().rawDamage().max() > 0f);
    }

    @Test
    void fastUnprimedMinecartInOpenSpaceDoesNotInventCollisionBurst() {
        WorldSnapshot.EntitySnapshot minecart = new WorldSnapshot.EntitySnapshot(
            "minecart:open",
            "minecraft:tnt_minecart",
            new Vec3Snapshot(3.5d, 0.5d, 0.5d),
            new Vec3Snapshot(0.2d, 0d, 0d),
            new AabbSnapshot(3d, 0d, 0d, 4d, 1d, 1d),
            Map.of()
        );

        boolean hasBurst = new ExplosionPredictor().predict(context(List.of(minecart), List.of())).stream()
            .anyMatch(candidate -> candidate.id().startsWith("burst:minecart-collision:"));

        assertTrue(!hasBurst, "open-space motion must not be mistaken for a crash detonation");
    }

    @Test
    void slowUnprimedMinecartAgainstWallStaysBelowVanillaCrashThreshold() {
        WorldSnapshot.EntitySnapshot minecart = new WorldSnapshot.EntitySnapshot(
            "minecart:slow",
            "minecraft:tnt_minecart",
            new Vec3Snapshot(3.5d, 0.5d, 0.5d),
            new Vec3Snapshot(0.05d, 0d, 0d),
            new AabbSnapshot(3d, 0d, 0d, 4d, 1d, 1d),
            Map.of()
        );
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(4.5d, 0.5d, 0.5d),
            "minecraft:obsidian",
            true,
            Map.of("full_collision_cube", "true")
        );

        boolean hasBurst = new ExplosionPredictor().predict(context(List.of(minecart), List.of(wall))).stream()
            .anyMatch(candidate -> candidate.id().startsWith("burst:minecart-collision:"));

        assertTrue(!hasBurst, "horizontal speed squared below 0.01 must not create a crash burst");
    }

    private static PredictionContext context(
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks
    ) {
        Vec3Snapshot position = new Vec3Snapshot(0.3d, 0d, 0.3d);
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
            new AabbSnapshot(0d, 0d, 0d, 0.6d, 1.8d, 0.6d),
            position,
            new Vec3Snapshot(0d, 0d, 0d),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, blocks),
            new TimingSnapshot(0, 100d, 10d, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
