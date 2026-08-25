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

import static org.junit.jupiter.api.Assertions.assertEquals;

class WitherExplosionCenterOffsetTest {
    @Test
    void fusedEntityExplosionUsesExplicitCenterYOffset() {
        WorldSnapshot.EntitySnapshot wither = new WorldSnapshot.EntitySnapshot(
            "wither:spawn",
            "minecraft:wither",
            new Vec3Snapshot(3, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(2.55, 0, -0.45, 3.45, 3.5, 0.45),
            Map.of(
                "explosion_radius", "7",
                "fuse_ticks", "20",
                "explosion_center_y_offset", "2.975"
            )
        );

        ThreatEvent event = new ExplosionPredictor().predict(context(wither)).getFirst();

        assertEquals(20L, event.impact().earliest());
        assertEquals(2.975d, event.sourcePosition().orElseThrow().y(), 0.000001d);
    }

    private static PredictionContext context(WorldSnapshot.EntitySnapshot entity) {
        Vec3Snapshot position = new Vec3Snapshot(0.3, 0, 0.3);
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            position, new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(entity), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
