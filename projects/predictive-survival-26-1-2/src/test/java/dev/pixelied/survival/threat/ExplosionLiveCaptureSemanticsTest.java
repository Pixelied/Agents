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
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionLiveCaptureSemanticsTest {
    @Test
    void predictorUsesSourceSpecificExplosionCenterInsteadOfEntityFeet() {
        WorldSnapshot.EntitySnapshot tnt = new WorldSnapshot.EntitySnapshot(
            "tnt:center",
            "minecraft:tnt",
            new Vec3Snapshot(2.0, 0.0, 0.3),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(1.75, 0, 0.05, 2.25, 0.98, 0.55),
            Map.of(
                "explosion_radius_default", "4.0",
                "explosion_radius_hidden_min", "0.0",
                "explosion_radius_hidden_max", "128.0",
                "server_hidden_explosion_power", "true",
                "explosion_center_x", "2.0",
                "explosion_center_y", "0.06125",
                "explosion_center_z", "0.3",
                "fuse_ticks", "1",
                "countdown_server_synchronized", "true",
                "source_key", "minecraft:explosion"
            )
        );

        ThreatEvent event = new ExplosionPredictor().predict(context(tnt, SafetyMode.BALANCED)).getFirst();

        assertEquals(new Vec3Snapshot(2.0, 0.06125, 0.3), event.sourcePosition().orElseThrow());
        assertEquals(event.sourcePosition(), event.impactPosition());
    }

    @Test
    void balancedMinecartDefaultRangeKeepsVanillaSpeedRandomnessWithoutUsingCustomNbtMaximum() {
        WorldSnapshot.EntitySnapshot minecart = new WorldSnapshot.EntitySnapshot(
            "minecart:default-range",
            "minecraft:tnt_minecart",
            new Vec3Snapshot(2.0, 0.0, 0.3),
            new Vec3Snapshot(1, 0, 0),
            new AabbSnapshot(1.5, 0, -0.2, 2.5, 0.7, 0.8),
            Map.of(
                "explosion_radius_default_min", "4.0",
                "explosion_radius_default_max", "11.5",
                "explosion_radius_hidden_min", "0.0",
                "explosion_radius_hidden_max", "1088.0",
                "server_hidden_explosion_power", "true",
                "fuse_ticks_min", "0",
                "fuse_ticks_max", "1",
                "source_key", "minecraft:explosion"
            )
        );

        ThreatEvent event = new ExplosionPredictor().predict(context(minecart, SafetyMode.BALANCED)).getFirst();

        assertEquals(Confidence.POTENTIAL, event.confidence());
        assertTrue(event.damage().rawDamage().max() > event.damage().rawDamage().min());
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot entity, SafetyMode mode) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(entity), List.of()),
            new TimingSnapshot(0, 0, 0, new TickWindow(0, 0)),
            EngineLimits.defaults(),
            mode
        );
    }
}
