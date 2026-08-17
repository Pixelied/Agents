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
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ThreatPredictorRegistryTest {
    @Test
    void duplicatePhysicalThreatsMergeToWiderBounds() {
        ThreatPredictor one = ignored -> List.of(event("crystal:7", 8f, 10f, 2, 3));
        ThreatPredictor two = ignored -> List.of(event("crystal:7", 9f, 12f, 3, 5));
        ThreatEvent merged = new ThreatPredictorRegistry(List.of(one, two)).predictAll(context(EngineLimits.defaults())).getFirst();

        assertEquals(new DamageRange(8f, 12f), merged.damage().rawDamage());
        assertEquals(new TickWindow(2, 5), merged.impact());
    }

    @Test
    void duplicateThreatOnlyKeepsGuaranteedDefensiveCapabilities() {
        ThreatPredictor optimistic = ignored -> List.of(event("same", 8f, 8f, 2, 2, true, true, true, false));
        ThreatPredictor conservative = ignored -> List.of(event("same", 8f, 8f, 2, 2, false, false, false, true));

        ThreatEvent merged = new ThreatPredictorRegistry(List.of(optimistic, conservative))
            .predictAll(context(EngineLimits.defaults())).getFirst();

        assertFalse(merged.avoidable());
        assertFalse(merged.blockable());
        assertFalse(merged.relocatable());
        assertEquals(true, merged.canDisableBlocking());
    }

    @Test
    void registryNeverReturnsMoreThanConfiguredThreatCap() {
        List<ThreatEvent> events = new ArrayList<>();
        for (int i = 0; i < 20; i++) events.add(event("e" + i, i + 1, i + 1, i % 4, i % 4));
        ThreatPredictor predictor = ignored -> events;
        List<ThreatEvent> result = new ThreatPredictorRegistry(List.of(predictor))
            .predictAll(context(new EngineLimits(8, 32, 80, 128)));

        assertEquals(8, result.size());
        assertThrows(UnsupportedOperationException.class, () -> result.add(event("extra", 1, 1, 0, 0)));
    }

    @Test
    void invalidLimitsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new EngineLimits(0, 32, 80, 128));
        assertThrows(IllegalArgumentException.class, () -> new EngineLimits(8, -1, 80, 128));
    }

    private static PredictionContext context(EngineLimits limits) {
        PlayerSnapshot player = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            limits
        );
    }

    private static ThreatEvent event(String id, float min, float max, long earliest, long latest) {
        return event(id, min, max, earliest, latest, true, true, true, false);
    }

    private static ThreatEvent event(
        String id, float min, float max, long earliest, long latest,
        boolean avoidable, boolean blockable, boolean relocatable, boolean canDisableBlocking
    ) {
        return new ThreatEvent(
            id,
            ThreatKind.OTHER,
            new TickWindow(earliest, latest),
            new DamageSourceSnapshot(new DamageRange(min, max), Set.of(), false, 1f, false, Optional.empty(), "test:" + id),
            Confidence.BOUNDED,
            Optional.empty(), Optional.empty(), avoidable, blockable, relocatable, canDisableBlocking
        );
    }
}
