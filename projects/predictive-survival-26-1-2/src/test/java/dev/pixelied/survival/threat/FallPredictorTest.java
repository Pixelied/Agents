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
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FallPredictorTest {
    private final FallPredictor predictor = new FallPredictor();

    @Test
    void slimeLandingIsZeroDamage() {
        assertTrue(predictor.predict(landingContext("minecraft:slime_block")).isEmpty());
    }

    @Test
    void hayUsesPointTwoFallMultiplier() {
        float stone = predictor.predict(landingContext("minecraft:stone")).getFirst().damage().rawDamage().max();
        float hay = predictor.predict(landingContext("minecraft:hay_block")).getFirst().damage().rawDamage().max();

        assertEquals(stone * 0.2f, hay, 0.01f);
    }

    @Test
    void landingDamageBypassesShieldWithArmorBypassTag() {
        ThreatEvent event = predictor.predict(landingContext("minecraft:stone")).getFirst();

        assertEquals("minecraft:fall", event.damage().sourceKey());
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_ARMOR));
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_SHIELD));
        assertFalse(event.blockable());
    }

    @Test
    void voidThreatBypassesDeathProtection() {
        ThreatEvent event = predictor.predict(voidContext()).getFirst();

        assertEquals("minecraft:out_of_world", event.damage().sourceKey());
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_ARMOR));
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_SHIELD));
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_INVULNERABILITY));
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_RESISTANCE));
        assertFalse(event.blockable());
    }

    @Test
    void voidDamageRepeatsEveryServerTickAfterCrossing() {
        List<ThreatEvent> events = predictor.predict(voidContext());

        assertTrue(events.size() >= 5);
        long first = events.getFirst().impact().earliest();
        for (int i = 0; i < 5; i++) {
            assertEquals(new TickWindow(first + i, first + i), events.get(i).impact());
            assertEquals("minecraft:out_of_world", events.get(i).damage().sourceKey());
        }
    }

    @Test
    void elytraWallDamageBypassesShieldWithArmorBypassTag() {
        ThreatEvent event = predictor.predict(elytraWallContext()).stream()
            .filter(threat -> threat.id().equals("fall:elytra_wall"))
            .findFirst()
            .orElseThrow();

        assertEquals("minecraft:fly_into_wall", event.damage().sourceKey());
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_ARMOR));
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_SHIELD));
        assertFalse(event.blockable());
    }

    @Test
    void landingPredictionUsesFutureGeometryAndCurrentFallState() {
        LandingPrediction landing = new FallLandingSolver().solve(landingContext("minecraft:stone")).orElseThrow();

        assertTrue(landing.tick() > 0);
        assertEquals("minecraft:stone", landing.surfaceBlockId());
        assertTrue(landing.rawFallDamage().max() > 0f);
    }

    private static PredictionContext landingContext(String surfaceBlockId) {
        PlayerSnapshot player = player(
            new Vec3Snapshot(0.2, 8, 0.2),
            new Vec3Snapshot(0, -1, 0),
            new AabbSnapshot(0.2, 8, 0.2, 0.8, 9.8, 0.8),
            Map.of(
                "fall_distance", "6",
                "safe_fall_distance", "3",
                "fall_damage_multiplier", "1",
                "world_min_y", "-64"
            )
        );
        WorldSnapshot.BlockSnapshot surface = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(0, 0, 0),
            surfaceBlockId,
            true,
            Map.of("full_collision_cube", "true")
        );
        return context(player, List.of(surface));
    }

    private static PredictionContext voidContext() {
        PlayerSnapshot player = player(
            new Vec3Snapshot(0, -126.5, 0),
            new Vec3Snapshot(0, -1, 0),
            new AabbSnapshot(0, -126.5, 0, 0.6, -124.7, 0.6),
            Map.of(
                "fall_distance", "20",
                "safe_fall_distance", "3",
                "fall_damage_multiplier", "1",
                "world_min_y", "-64"
            )
        );
        return context(player, List.of());
    }

    private static PredictionContext elytraWallContext() {
        PlayerSnapshot player = player(
            new Vec3Snapshot(0.2, 1, 0.2),
            new Vec3Snapshot(1, 0, 0),
            new AabbSnapshot(0.2, 1, 0.2, 0.8, 2.8, 0.8),
            Map.of("fall_flying", "true", "world_min_y", "-64")
        );
        WorldSnapshot.BlockSnapshot wall = new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(1, 1, 0),
            "minecraft:stone",
            true,
            Map.of("full_collision_cube", "true")
        );
        return context(player, List.of(wall));
    }

    private static PlayerSnapshot player(
        Vec3Snapshot position,
        Vec3Snapshot velocity,
        AabbSnapshot box,
        Map<String, String> state
    ) {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), box, position, velocity, Map.of(), state
        );
    }

    private static PredictionContext context(PlayerSnapshot player, List<WorldSnapshot.BlockSnapshot> blocks) {
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(), blocks),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
