package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
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

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InfiniteStatusEffectPredictorTest {
    private final StatusEffectPredictor predictor = new StatusEffectPredictor();

    @Test
    void infiniteDurationSentinelIsRepresentable() {
        EffectInstanceSnapshot effect = new EffectInstanceSnapshot("minecraft:wither", -1, 0);
        assertEquals(-1, effect.durationTicks());
    }

    @Test
    void infiniteWitherUsesUnknownPhaseCadenceWindows() {
        List<ThreatEvent> wither = predictor.predict(context(
            new EffectInstanceSnapshot("minecraft:wither", -1, 0)
        )).stream().filter(event -> "minecraft:wither".equals(event.damage().sourceKey())).toList();

        assertEquals(4, wither.size());
        assertEquals(List.of(
            new TickWindow(1, 40),
            new TickWindow(41, 80),
            new TickWindow(81, 120),
            new TickWindow(121, 128)
        ), wither.stream().map(ThreatEvent::impact).toList());
        assertEquals(List.of(
            DamageRange.exact(1f),
            DamageRange.exact(1f),
            DamageRange.exact(1f),
            new DamageRange(0f, 1f)
        ), wither.stream().map(event -> event.damage().rawDamage()).toList());
        assertTrue(wither.subList(0, 3).stream().allMatch(event -> event.confidence() == Confidence.BOUNDED));
        assertEquals(Confidence.POTENTIAL, wither.getLast().confidence());
        assertTrue(wither.stream().allMatch(event -> event.damage().applicationHealthThresholdExclusive() == 0f));
    }

    @Test
    void infinitePoisonKeepsOneHealthFloorAcrossUnknownPhaseWindows() {
        List<ThreatEvent> poison = predictor.predict(context(
            new EffectInstanceSnapshot("minecraft:poison", -1, 0)
        )).stream().filter(event -> "minecraft:magic".equals(event.damage().sourceKey())).toList();

        assertEquals(new TickWindow(1, 25), poison.getFirst().impact());
        assertEquals(DamageRange.exact(1f), poison.getFirst().damage().rawDamage());
        assertEquals(1f, poison.getFirst().damage().applicationHealthThresholdExclusive(), 0.0001f);
        assertEquals(Confidence.BOUNDED, poison.getFirst().confidence());
    }

    private static PredictionContext context(EffectInstanceSnapshot effect) {
        StatusEffectsSnapshot effects = new StatusEffectsSnapshot(false, -1, Map.of(effect.effectKey(), effect));
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), effects, BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
