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
import dev.pixelied.survival.damage.DamageFlag;
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

class ShulkerBulletPredictorTest {
    @Test
    void visibleShulkerBulletProducesPotentialMobProjectileThreat() {
        ThreatEvent event = new ShulkerBulletPredictor().predict(context(DifficultySnapshot.NORMAL)).getFirst();

        assertEquals(Confidence.POTENTIAL, event.confidence());
        assertEquals(new TickWindow(1, EngineLimits.defaults().maxProjectileHorizonTicks()), event.impact());
        assertEquals(4f, event.damage().rawDamage().min(), 0.0001f);
        assertEquals(4f, event.damage().rawDamage().max(), 0.0001f);
        assertEquals("minecraft:mob_projectile", event.damage().sourceKey());
        assertTrue(event.damage().flags().contains(DamageFlag.IS_PROJECTILE));
        assertTrue(event.blockable());
        assertTrue(event.avoidable());
    }

    @Test
    void opaqueOwnerKeepsHardDifficultyUpperBound() {
        ThreatEvent event = new ShulkerBulletPredictor().predict(context(DifficultySnapshot.HARD)).getFirst();

        assertEquals(4f, event.damage().rawDamage().min(), 0.0001f);
        assertEquals(6f, event.damage().rawDamage().max(), 0.0001f);
    }

    private static PredictionContext context(DifficultySnapshot difficulty) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, difficulty,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        WorldSnapshot.EntitySnapshot bullet = new WorldSnapshot.EntitySnapshot(
            "bullet:1",
            "minecraft:shulker_bullet",
            new Vec3Snapshot(0.3, 1.0, 8.0),
            new Vec3Snapshot(0, 0, -0.15),
            new AabbSnapshot(0.15, 0.85, 7.85, 0.45, 1.15, 8.15),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(bullet), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
