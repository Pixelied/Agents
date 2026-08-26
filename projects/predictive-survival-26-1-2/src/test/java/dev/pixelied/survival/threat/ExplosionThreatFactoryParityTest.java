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
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionThreatFactoryParityTest {
    @Test
    void sharedFactoryBuildsVanillaExplosionThreatWithExistingSemantics() {
        PredictionContext context = contextAt(new Vec3Snapshot(0.3, 0, 0.3));
        TickWindow impact = new TickWindow(0, 2);
        ExplosionSpec spec = new ExplosionSpec(
            new Vec3Snapshot(3, 0, 0), 6f, 6f,
            "minecraft:explosion", false, true
        );

        ThreatEvent event = new ExplosionThreatFactory().create(
            "opportunity:test:crystal", impact, Confidence.POTENTIAL,
            spec, context, new SnapshotOcclusionView(context.world().blocks())
        ).orElseThrow();

        assertEquals(impact, event.impact());
        assertEquals("minecraft:explosion", event.damage().sourceKey());
        assertTrue(event.damage().flags().contains(DamageFlag.IS_EXPLOSION));
        assertTrue(event.damage().rawDamage().max() > 0f);
    }

    private static PredictionContext contextAt(Vec3Snapshot position) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(position.x() - 0.3, position.y(), position.z() - 0.3,
                position.x() + 0.3, position.y() + 1.8, position.z() + 0.3),
            position, new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player, new WorldSnapshot(List.of(), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)), EngineLimits.defaults()
        );
    }
}
