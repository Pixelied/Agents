package dev.pixelied.survival.timeline;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class HealthBoostExpiryTimelineTest {
    @Test
    void expiringHealthBoostClampsHealthBeforeLaterDamage() {
        StatusEffectsSnapshot effects = new StatusEffectsSnapshot(
            false,
            -1,
            Map.of("minecraft:health_boost", new EffectInstanceSnapshot("minecraft:health_boost", 1, 1))
        );
        PlayerSnapshot player = new PlayerSnapshot(
            24f,
            0f,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(),
            effects,
            BlockingSnapshot.none(),
            HurtState.unknown(),
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of(),
            Map.of("max_health", "28.0")
        );
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(21f),
            Set.of(DamageFlag.BYPASSES_COOLDOWN),
            false,
            1f,
            false,
            Optional.empty(),
            "test:post_health_boost"
        );
        ThreatEvent event = new ThreatEvent(
            "post-expiry-hit",
            ThreatKind.OTHER,
            new TickWindow(2, 2),
            damage,
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            false
        );

        TimelineResult result = new ThreatTimelineSimulator().simulate(
            player,
            new ThreatTimeline(List.of(event))
        );

        assertFalse(result.survived(), "Health Boost expiry must remove its +8 max-health modifier before damage");
        assertEquals(0f, result.finalHealth(), 0.0001f);
    }
}
