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

class NetworkAgedExplosionDeadlineTest {
    @Test
    void observedFuseIsAgedBeforePlannerDeadline() {
        TimingSnapshot timing = new TimingSnapshot(100, 200, 25, new TickWindow(101, 104));
        TickWindow age = timing.observationAgeWindow();

        TickWindow serverFuse = ExplosionTiming.ageCountdown(5, age);

        assertEquals(new TickWindow(1, 4), serverFuse);
        assertTrue(serverFuse.earliest() < 5);
    }

    @Test
    void countdownSaturatesAtImmediateInsteadOfGoingNegative() {
        assertEquals(
            new TickWindow(0, 1),
            ExplosionTiming.ageCountdown(2, new TickWindow(1, 5))
        );
    }

    @Test
    void synchronizedTntFuseUsesAgedServerRelativeImpactWindow() {
        TimingSnapshot timing = new TimingSnapshot(100, 200, 25, new TickWindow(101, 104));
        WorldSnapshot.EntitySnapshot tnt = new WorldSnapshot.EntitySnapshot(
            "tnt:aged",
            "minecraft:tnt",
            new Vec3Snapshot(2.0, 0.0625, 0.3),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(1.75, 0, 0.05, 2.25, 0.98, 0.55),
            Map.of(
                "explosion_radius", "4.0",
                "fuse_ticks", "5",
                "countdown_server_synchronized", "true",
                "source_key", "minecraft:explosion",
                "scales_with_difficulty", "true"
            )
        );

        var event = new ExplosionPredictor().predict(context(timing, tnt)).getFirst();

        assertEquals(new TickWindow(1, 4), event.impact());
    }

    private static PredictionContext context(TimingSnapshot timing, WorldSnapshot.EntitySnapshot entity) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0, 0.3), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player, new WorldSnapshot(List.of(entity), List.of()), timing, EngineLimits.defaults()
        );
    }
}
