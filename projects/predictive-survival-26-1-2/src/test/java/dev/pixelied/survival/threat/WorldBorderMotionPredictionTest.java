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

import static org.junit.jupiter.api.Assertions.assertTrue;

class WorldBorderMotionPredictionTest {
    @Test
    void observedOutwardVelocityCanIncreaseFutureBorderDamage() {
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
            new AabbSnapshot(-6.3, 0, 0, -5.7, 1.8, 0.6),
            new Vec3Snapshot(-6, 0, 0),
            new Vec3Snapshot(-1, 0, 0),
            Map.of(),
            Map.of(
                "border_distance_plus_safe_zone", "-1",
                "border_damage_per_block", "1",
                "border_safe_zone", "5",
                "border_min_x", "0",
                "border_max_x", "100",
                "border_min_z", "-100",
                "border_max_z", "100",
                "border_lerp_ticks", "0",
                "border_lerp_target_size", "100"
            )
        );
        PredictionContext context = new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );

        List<ThreatEvent> border = new WorldBorderPredictor().predict(context);
        float first = border.stream().filter(e -> e.impact().earliest() == 1).findFirst().orElseThrow()
            .damage().rawDamage().max();
        float later = border.stream().filter(e -> e.impact().earliest() == 4).findFirst().orElseThrow()
            .damage().rawDamage().max();

        assertTrue(later > first,
            "continuing the already-observed outward movement must not reuse a stale lower border damage value");
    }
}
