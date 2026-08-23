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
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusEffectPredictorPhaseTest {
    private final StatusEffectPredictor predictor = new StatusEffectPredictor();

    @Test
    void poisonDurationAtApplicationBoundaryHitsOnNextServerTick() {
        ThreatEvent next = predictor.predict(context(effect("minecraft:poison", 100, 0))).getFirst();

        assertEquals(new TickWindow(1, 1), next.impact());
    }

    @Test
    void poisonBetweenBoundariesUsesCurrentRemainingDurationPhase() {
        ThreatEvent next = predictor.predict(context(effect("minecraft:poison", 99, 0))).getFirst();

        assertEquals(new TickWindow(25, 25), next.impact());
    }

    @Test
    void witherDurationAtApplicationBoundaryHitsOnNextServerTick() {
        ThreatEvent next = predictor.predict(context(effect("minecraft:wither", 80, 0))).getFirst();

        assertEquals(new TickWindow(1, 1), next.impact());
    }

    @Test
    void witherBetweenBoundariesUsesCurrentRemainingDurationPhase() {
        ThreatEvent next = predictor.predict(context(effect("minecraft:wither", 79, 0))).getFirst();

        assertEquals(new TickWindow(40, 40), next.impact());
    }

    private static PredictionContext context(EffectInstanceSnapshot effect) {
        Map<String, EffectInstanceSnapshot> effects = new LinkedHashMap<>();
        effects.put(effect.effectKey(), effect);
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            new StatusEffectsSnapshot(false, -1, effects),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of(),
            Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }

    private static EffectInstanceSnapshot effect(String key, int duration, int amplifier) {
        return new EffectInstanceSnapshot(key, duration, amplifier);
    }
}
