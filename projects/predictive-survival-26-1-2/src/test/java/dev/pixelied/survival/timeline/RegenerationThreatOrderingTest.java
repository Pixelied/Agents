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

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;

class RegenerationThreatOrderingTest {
    @Test
    void tickOneEnvironmentalDamageCannotUseSameTickRegeneration() {
        StatusEffectsSnapshot effects = new StatusEffectsSnapshot(
            false,
            -1,
            Map.of("minecraft:regeneration", new EffectInstanceSnapshot("minecraft:regeneration", 50, 0))
        );
        PlayerSnapshot player = new PlayerSnapshot(
            1f,
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
            Map.of("max_health", "20")
        );
        DamageSourceSnapshot inWall = new DamageSourceSnapshot(
            DamageRange.exact(1f),
            EnumSet.of(DamageFlag.BYPASSES_ARMOR, DamageFlag.BYPASSES_SHIELD),
            false,
            1f,
            false,
            Optional.empty(),
            "minecraft:in_wall"
        );
        ThreatEvent threat = new ThreatEvent(
            "env:in_wall:1",
            ThreatKind.ENVIRONMENT,
            new TickWindow(1, 1),
            inWall,
            Confidence.EXACT,
            Optional.empty(),
            Optional.empty(),
            true,
            false,
            true,
            false
        );

        TimelineResult result = new ThreatTimelineSimulator().simulate(player, new ThreatTimeline(List.of(threat)));

        assertFalse(result.survived(),
            "vanilla environmental base-tick damage happens before the Regeneration effect tick");
    }
}
