package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTimelineSimulator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

class ThreatCapOverflowSafetyTest {
    @Test
    void topLevelThreatCapCannotHideLaterLethalRisk() {
        PredictionContext context = context(3);
        ThreatPredictor burst = ignored -> overflowingThreats();

        List<ThreatEvent> capped = new ThreatPredictorRegistry(List.of(burst)).predictAll(context);

        assertFalse(
            new ThreatTimelineSimulator().simulate(context.player(), new ThreatTimeline(capped)).survived(),
            "filling the cap with earlier one-damage events must not silently discard a later lethal threat"
        );
    }

    @Test
    void environmentThreatCapCannotHideLaterLethalRiskBeforeTopLevelMerge() {
        PredictionContext context = context(3);
        ThreatPredictor burst = ignored -> overflowingThreats();

        List<ThreatEvent> capped = new EnvironmentPredictorRegistry(List.of(burst)).predict(context);

        assertFalse(
            new ThreatTimelineSimulator().simulate(context.player(), new ThreatTimeline(capped)).survived(),
            "the environment sub-registry must not erase lethal overflow before the top-level registry can see it"
        );
    }

    private static List<ThreatEvent> overflowingThreats() {
        return List.of(
            threat("early:1", 1, 1f),
            threat("early:2", 2, 1f),
            threat("early:3", 3, 1f),
            threat("late:lethal", 4, 100f)
        );
    }

    private static ThreatEvent threat(String id, long tick, float rawDamage) {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage), Set.of(), false, 1f, false, Optional.empty(), "minecraft:generic"
        );
        return new ThreatEvent(
            id,
            ThreatKind.OTHER,
            new TickWindow(tick, tick),
            source,
            dev.pixelied.survival.core.Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            false
        );
    }

    private static PredictionContext context(int maxThreats) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of()),
            new TimingSnapshot(0, 0, 0, new TickWindow(0, 0)),
            new EngineLimits(maxThreats, 32, 80, 128)
        );
    }
}
