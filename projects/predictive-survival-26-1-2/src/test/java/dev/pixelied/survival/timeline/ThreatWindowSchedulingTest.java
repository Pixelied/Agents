package dev.pixelied.survival.timeline;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.EffectInstanceSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ThreatWindowSchedulingTest {
    @Test
    void survivalMustHoldForEarliestAsWellAsLatestImpact() {
        PlayerSnapshot player = new PlayerSnapshot(
            1f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            new StatusEffectsSnapshot(
                false,
                -1,
                Map.of("minecraft:regeneration", new EffectInstanceSnapshot("minecraft:regeneration", 900, 1))
            ),
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of(),
            Map.of("max_health", "20")
        );
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(1f),
            Set.of(),
            false,
            1f,
            false,
            Optional.empty(),
            "test:window"
        );
        ThreatEvent event = new ThreatEvent(
            "window",
            ThreatKind.OTHER,
            new TickWindow(1, 20),
            damage,
            Confidence.BOUNDED,
            Optional.empty(),
            Optional.empty(),
            true,
            true,
            true,
            false
        );

        TimelineResult result = new ThreatTimelineSimulator().simulate(player, new ThreatTimeline(List.of(event)));

        assertFalse(result.survived(),
            "a bounded impact window is safe only if every feasible impact time survives");
        assertEquals("window", result.firstLethalEventId().orElseThrow());
    }
}
