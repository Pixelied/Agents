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

class WardenSonicBoomPredictorTest {
    @Test
    void activeSonicChargeInRangeProducesPotentialThreat() {
        ThreatEvent event = new WardenSonicBoomPredictor().predict(context(
            new Vec3Snapshot(0, 0, 10), Map.of("warden_sonic_ticks", "20")
        )).getFirst();

        assertEquals(Confidence.POTENTIAL, event.confidence());
        assertEquals(new TickWindow(0, 14), event.impact());
        assertEquals(10f, event.damage().rawDamage().min(), 0.0001f);
        assertEquals(10f, event.damage().rawDamage().max(), 0.0001f);
        assertEquals("minecraft:sonic_boom", event.damage().sourceKey());
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_ARMOR));
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_SHIELD));
        assertTrue(event.damage().flags().contains(DamageFlag.BYPASSES_ENCHANTMENTS));
    }

    @Test
    void playerOutsideSonicRangeProducesNoThreat() {
        assertTrue(new WardenSonicBoomPredictor().predict(context(
            new Vec3Snapshot(0, 0, 16), Map.of("warden_sonic_ticks", "10")
        )).isEmpty());
    }

    @Test
    void chargePastDamageMomentProducesNoFutureThreat() {
        assertTrue(new WardenSonicBoomPredictor().predict(context(
            new Vec3Snapshot(0, 0, 10), Map.of("warden_sonic_ticks", "35")
        )).isEmpty());
    }

    private static PredictionContext context(Vec3Snapshot wardenPosition, Map<String, String> properties) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        WorldSnapshot.EntitySnapshot warden = new WorldSnapshot.EntitySnapshot(
            "warden:1", "minecraft:warden", wardenPosition, new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(wardenPosition.x() - 0.45, wardenPosition.y(), wardenPosition.z() - 0.45,
                wardenPosition.x() + 0.45, wardenPosition.y() + 2.9, wardenPosition.z() + 0.45),
            properties
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(warden), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
