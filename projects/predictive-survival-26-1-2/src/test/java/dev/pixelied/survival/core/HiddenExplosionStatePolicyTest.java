package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.planner.SafetyMode;
import dev.pixelied.survival.threat.ExplosionPredictor;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HiddenExplosionStatePolicyTest {
    @Test
    void balancedUsesVanillaDefaultButDoesNotPretendHiddenTntPowerIsExact() {
        ThreatEvent event = new ExplosionPredictor().predict(context(SafetyMode.BALANCED)).getFirst();

        assertEquals(Confidence.POTENTIAL, event.confidence());
        assertTrue(event.damage().rawDamage().max() > 0f);
    }

    @Test
    void safeWidensToSourceSupportedHiddenTntPowerBounds() {
        ThreatEvent balanced = new ExplosionPredictor().predict(context(SafetyMode.BALANCED)).getFirst();
        ThreatEvent safe = new ExplosionPredictor().predict(context(SafetyMode.SAFE)).getFirst();

        assertEquals(Confidence.BOUNDED, safe.confidence());
        assertTrue(
            safe.damage().rawDamage().max() > balanced.damage().rawDamage().max(),
            "SAFE must account for source-supported hidden custom TNT power instead of silently trusting the client default"
        );
    }

    private static PredictionContext context(SafetyMode safetyMode) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0.0, 0.0, 0.0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0.0, 0.3), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        WorldSnapshot.EntitySnapshot tnt = new WorldSnapshot.EntitySnapshot(
            "tnt:hidden-power",
            "minecraft:tnt",
            new Vec3Snapshot(2.0, 0.0625, 0.3),
            new Vec3Snapshot(0, 0, 0),
            new AabbSnapshot(1.75, 0, 0.05, 2.25, 0.98, 0.55),
            Map.of(
                "explosion_radius_default", "4.0",
                "explosion_radius_hidden_min", "0.0",
                "explosion_radius_hidden_max", "128.0",
                "server_hidden_explosion_power", "true",
                "fuse_ticks", "5",
                "source_key", "minecraft:explosion",
                "scales_with_difficulty", "true"
            )
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(tnt), List.of()),
            new TimingSnapshot(0, 0, 0, new TickWindow(0, 0)),
            EngineLimits.defaults(),
            safetyMode
        );
    }
}
