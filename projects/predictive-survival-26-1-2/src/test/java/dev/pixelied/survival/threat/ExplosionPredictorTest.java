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
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static dev.pixelied.survival.damage.DamageFlag.IS_EXPLOSION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionPredictorTest {
    @Test
    void tntFuseProducesExactImpactTick() {
        WorldSnapshot.EntitySnapshot tnt = entity("tnt:1", "minecraft:tnt", Map.of(
            "explosion_radius", "4.0",
            "fuse_ticks", "80"
        ));

        ThreatEvent event = new ExplosionPredictor().predict(context(List.of(tnt))).getFirst();
        assertEquals(new TickWindow(80, 80), event.impact());
        assertEquals(Confidence.EXACT, event.confidence());
        assertTrue(event.damage().flags().contains(IS_EXPLOSION));
    }

    @Test
    void triggerableCrystalWithoutFuseIsPotentialImmediateThreat() {
        WorldSnapshot.EntitySnapshot crystal = entity("crystal:7", "minecraft:end_crystal", Map.of(
            "explosion_radius", "6.0",
            "triggerable", "true"
        ));

        ThreatEvent event = new ExplosionPredictor().predict(context(List.of(crystal))).getFirst();
        assertEquals(Confidence.POTENTIAL, event.confidence());
        assertEquals(new TickWindow(0, 2), event.impact());
    }

    private static PredictionContext context(List<WorldSnapshot.EntitySnapshot> entities) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(entities, List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static WorldSnapshot.EntitySnapshot entity(String id, String type, Map<String, String> properties) {
        return new WorldSnapshot.EntitySnapshot(
            id, type, new Vec3Snapshot(3, 0, 0), new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(3, 0, 0, 4, 1, 1), properties
        );
    }
}
