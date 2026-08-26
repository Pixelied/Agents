package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;
import dev.pixelied.survival.timeline.ThreatTimeline;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaDamageOracleTest {
    @Test
    void lethalWithoutDeathProtectionRemovesOnlyHandProtectionForSimulation() {
        PlayerSnapshot protectedPlayer = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.offHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0.3, 0, 0.3), new Vec3Snapshot(0, 0, 0), Map.of()
        );
        ThreatTimeline timeline = new ThreatTimeline(List.of(new ThreatEvent(
            "test:lethal",
            ThreatKind.OTHER,
            new TickWindow(0, 0),
            new DamageSourceSnapshot(
                DamageRange.exact(100f), Set.of(), false, 1f, false, Optional.empty(), "test:damage"
            ),
            Confidence.EXACT,
            Optional.empty(), Optional.empty(), false, false, false, false
        )));

        VanillaDamageOracle oracle = new VanillaDamageOracle();

        assertTrue(oracle.simulate(protectedPlayer, timeline).survived());
        assertTrue(oracle.lethalWithoutDeathProtection(protectedPlayer, timeline));
    }
}
