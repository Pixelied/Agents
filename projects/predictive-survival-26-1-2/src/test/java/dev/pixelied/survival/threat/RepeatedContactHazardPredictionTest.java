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
import dev.pixelied.survival.timeline.ThreatTimeline;
import dev.pixelied.survival.timeline.ThreatTimelineSimulator;
import dev.pixelied.survival.timing.TimingSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatedContactHazardPredictionTest {
    @Test
    void sustainedCactusContactCannotBeModeledAsOneSafeHit() {
        PlayerSnapshot player = player();
        PredictionContext context = context(player, EngineLimits.defaults());

        var events = new ContactHazardPredictor().predict(context);
        var result = new ThreatTimelineSimulator().simulate(player, new ThreatTimeline(events));

        assertTrue(events.size() > 1, "continued current contact must include later potential damage attempts");
        assertFalse(result.survived(),
            "hurt cooldown rejects most cactus attempts, but a later accepted tick can still kill at two health");
    }

    @Test
    void sustainedContactUsesDecisionHorizonRatherThanProjectileHorizon() {
        PlayerSnapshot player = player();
        EngineLimits limits = new EngineLimits(256, 32, 3, 12);

        var events = new ContactHazardPredictor().predict(context(player, limits));

        assertTrue(events.stream().anyMatch(event -> event.impact().equals(new TickWindow(12, 12))),
            "current contact can persist through the full decision horizon even when projectile prediction is shorter");
        assertFalse(events.stream().anyMatch(event -> event.impact().earliest() > 12));
    }

    private static PlayerSnapshot player() {
        return new PlayerSnapshot(
            2f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            StatusEffectsSnapshot.none(),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of(),
            Map.of("contact_cactus", "true")
        );
    }

    private static PredictionContext context(PlayerSnapshot player, EngineLimits limits) {
        return new PredictionContext(
            player,
            new WorldSnapshot(List.of(), List.of()),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            limits
        );
    }
}
