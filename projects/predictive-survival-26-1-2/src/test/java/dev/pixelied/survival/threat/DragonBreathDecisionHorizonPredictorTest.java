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
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonBreathDecisionHorizonPredictorTest {
    private final ProjectilePredictor predictor = new ProjectilePredictor();

    @Test
    void dragonBreathConsequencesContinuePastTrajectoryHorizon() {
        List<ThreatEvent> breath = breathEvents(EngineLimits.defaults());

        assertTrue(breath.stream().anyMatch(event -> event.impact().earliest() > 80L));
        assertEquals(128L, breath.getLast().impact().latest());
    }

    @Test
    void dragonBreathConsequencesStopAtDecisionHorizon() {
        EngineLimits limits = new EngineLimits(128, 32, 80, 60);
        List<ThreatEvent> breath = breathEvents(limits);

        assertTrue(!breath.isEmpty());
        assertTrue(breath.stream().allMatch(event -> event.impact().latest() <= 60L));
        assertEquals(60L, breath.getLast().impact().latest());
    }

    private List<ThreatEvent> breathEvents(EngineLimits limits) {
        return predictor.predict(context(limits)).stream()
            .filter(event -> event.id().contains(":dragon_breath:"))
            .toList();
    }

    private static PredictionContext context(EngineLimits limits) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(6.7, 0, 0, 7.3, 1.8, 0.6),
            new Vec3Snapshot(6.7, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        WorldSnapshot.EntitySnapshot dragon = new WorldSnapshot.EntitySnapshot(
            "dragon:horizon",
            "minecraft:dragon_fireball",
            new Vec3Snapshot(0, 1.0, 0.3),
            new Vec3Snapshot(1.5, 0, 0),
            new AabbSnapshot(-0.5, 0.5, -0.2, 0.5, 1.5, 0.8),
            Map.of("acceleration_power", "0.0")
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(dragon), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            limits
        );
    }
}
