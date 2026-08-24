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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectilePartialCollisionSafetyTest {
    @Test
    void collidablePartialBlockCannotHideNearbySplashThreat() {
        WorldSnapshot.EntitySnapshot potion = new WorldSnapshot.EntitySnapshot(
            "splash:partial",
            "minecraft:splash_potion",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.125, 0.875, 0.175, 0.125, 1.125, 0.425),
            Map.of(
                "potion_instant_damage", "12.0",
                "potion_splash_radius", "4.0",
                "potion_source_key", "minecraft:indirect_magic"
            )
        );
        WorldSnapshot.BlockSnapshot partialCollision = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(5, 0, 0),
            "minecraft:oak_slab",
            true,
            Map.of("full_collision_cube", "false")
        );
        PlayerSnapshot player = player(new AabbSnapshot(6.7, 0, 3.0, 7.3, 1.8, 3.6));
        PredictionContext context = context(player, List.of(potion), List.of(partialCollision));

        List<ThreatEvent> events = new ProjectilePredictor().predict(context);

        assertFalse(
            events.isEmpty(),
            "a collidable slab/fence-like cell near the player must not be discarded from projectile collision safety"
        );
    }

    @Test
    void projectilePassingAboveKnownSlabBoundsStillThreatensPlayer() {
        WorldSnapshot.EntitySnapshot arrow = new WorldSnapshot.EntitySnapshot(
            "arrow:above-slab",
            "minecraft:arrow",
            new Vec3Snapshot(0.0, 0.75, 0.5),
            new Vec3Snapshot(1.0, 0.0, 0.0),
            new AabbSnapshot(-0.125, 0.625, 0.375, 0.125, 0.875, 0.625),
            Map.of("raw_damage", "4.0", "no_gravity", "true")
        );
        WorldSnapshot.BlockSnapshot slab = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1.5, 0.5, 0.5),
            "minecraft:oak_slab",
            true,
            Map.of(
                "full_collision_cube", "false",
                "collision_min_x", "0.0",
                "collision_min_y", "0.0",
                "collision_min_z", "0.0",
                "collision_max_x", "1.0",
                "collision_max_y", "0.5",
                "collision_max_z", "1.0"
            )
        );
        PlayerSnapshot player = player(new AabbSnapshot(2.6, 0.0, 0.2, 3.2, 1.8, 0.8));

        List<ThreatEvent> events = new ProjectilePredictor().predict(context(player, List.of(arrow), List.of(slab)));

        assertTrue(events.stream().anyMatch(event -> event.id().equals("projectile:arrow:above-slab:direct")),
            "known partial collision bounds must not invent a full-cube wall above a slab");
    }

    private static PredictionContext context(
        PlayerSnapshot player,
        List<WorldSnapshot.EntitySnapshot> entities,
        List<WorldSnapshot.BlockSnapshot> blocks
    ) {
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, blocks),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static PlayerSnapshot player(AabbSnapshot box) {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), box,
            new Vec3Snapshot(box.minX(), box.minY(), box.minZ()),
            new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }
}
