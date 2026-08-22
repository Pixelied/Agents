package dev.pixelied.survival.planner;

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
import dev.pixelied.survival.threat.ThreatPredictor;
import dev.pixelied.survival.threat.ThreatPredictorRegistry;
import dev.pixelied.survival.timing.TimingSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetadataPreservationTest {
    @Test
    void duplicateThreatMergeKeepsWorstCaseDamageMetadataAndSharedCausality() {
        ThreatEvent first = event("same", 8f, 2f, -0.25f, 1f, Optional.of("parent"));
        ThreatEvent second = event("same", 10f, 5f, 0.10f, 4f, Optional.of("parent"));
        ThreatPredictor one = ignored -> List.of(first);
        ThreatPredictor two = ignored -> List.of(second);

        ThreatEvent merged = new ThreatPredictorRegistry(List.of(one, two))
            .predictAll(context())
            .getFirst();

        assertEquals(2f, merged.damage().applicationHealthThresholdExclusive());
        assertEquals(-0.25f, merged.damage().armorEffectivenessAdjustment());
        assertEquals(4f, merged.damage().blockingDisableSeconds());
        assertEquals(Optional.of("parent"), merged.requiresAcceptedEventId());
    }

    @Test
    void duplicateThreatMergeDoesNotPromoteUnknownPositionsToKnown() {
        Vec3Snapshot knownPosition = new Vec3Snapshot(0, 0, 5);
        ThreatEvent unknown = event("same", 8f, 0f, 0f, 0f, Optional.empty());
        DamageSourceSnapshot knownDamage = new DamageSourceSnapshot(
            DamageRange.exact(8f), Set.of(), false, 1f, false,
            Optional.of(knownPosition), "test:same", 0f, 0f, 0f
        );
        ThreatEvent known = new ThreatEvent(
            "same", ThreatKind.MELEE, new TickWindow(2, 2), knownDamage, Confidence.EXACT,
            Optional.of(knownPosition), Optional.of(knownPosition), false, true, false, false, Optional.empty()
        );
        ThreatPredictor one = ignored -> List.of(unknown);
        ThreatPredictor two = ignored -> List.of(known);

        ThreatEvent merged = new ThreatPredictorRegistry(List.of(one, two))
            .predictAll(context())
            .getFirst();

        assertEquals(Optional.empty(), merged.damage().sourcePosition());
        assertEquals(Optional.empty(), merged.sourcePosition());
        assertEquals(Optional.empty(), merged.impactPosition());
    }

    @Test
    void placeCoverRawDamageOverridePreservesAllOtherThreatMetadata() {
        ThreatEvent original = event("child", 10f, 2f, -0.30f, 3f, Optional.of("parent"));
        SurvivalAction.PlaceCover cover = new SurvivalAction.PlaceCover(
            Map.of("child", DamageRange.exact(3f)),
            0, true, true, 1d, 0, 0
        );

        ThreatEvent copied = cover.applyTimeline(new ThreatTimeline(List.of(original))).events().getFirst();

        assertEquals(DamageRange.exact(3f), copied.damage().rawDamage());
        assertEquals(2f, copied.damage().applicationHealthThresholdExclusive());
        assertEquals(-0.30f, copied.damage().armorEffectivenessAdjustment());
        assertEquals(3f, copied.damage().blockingDisableSeconds());
        assertEquals(Optional.of("parent"), copied.requiresAcceptedEventId());
    }

    private static ThreatEvent event(
        String id,
        float rawDamage,
        float applicationThreshold,
        float armorAdjustment,
        float blockingDisableSeconds,
        Optional<String> prerequisite
    ) {
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(rawDamage),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "test:" + id,
            applicationThreshold,
            armorAdjustment,
            blockingDisableSeconds
        );
        return new ThreatEvent(
            id,
            ThreatKind.MELEE,
            new TickWindow(2, 2),
            damage,
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            false,
            true,
            false,
            blockingDisableSeconds > 0f,
            prerequisite
        );
    }

    private static PredictionContext context() {
        PlayerSnapshot player = new PlayerSnapshot(
            20f,
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
            Map.of()
        );
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 100, 10, new TickWindow(1, 2)),
            EngineLimits.defaults()
        );
    }
}
