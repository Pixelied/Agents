package dev.pixelied.survival.timeline;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.ArmorPieceSnapshot;
import dev.pixelied.survival.damage.BlockingSnapshot;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.pixelied.survival.damage.ArmorPieceSnapshot.Slot.CHEST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThreatTimelineSimulatorTest {
    private final ThreatTimelineSimulator simulator = new ThreatTimelineSimulator();

    @Test
    void individuallySafeHitsCanKillAsSequence() {
        TimelineResult result = simulator.simulate(
            player(10f, MitigationSnapshot.none(), DeathProtectionSnapshot.none()),
            new ThreatTimeline(List.of(event("first", 6f, 0), event("second", 6f, 21)))
        );

        assertFalse(result.survived());
        assertEquals("second", result.firstLethalEventId().orElseThrow());
        assertEquals(0f, result.finalHealth(), 0.0001f);
    }

    @Test
    void sameWindowUsesDescendingRawAsStableWorstCaseTieBreak() {
        TimelineResult result = simulator.simulate(
            player(20f, MitigationSnapshot.none(), DeathProtectionSnapshot.none()),
            new ThreatTimeline(List.of(event("small", 4f, 0), event("large", 12f, 0)))
        );

        assertEquals(12f, result.eventResults().getFirst().preMitigationRaw(), 0.0001f);
        assertEquals("large", result.eventResults().getFirst().event().id());
    }

    @Test
    void simulationContinuesAfterPopAndCanStillDie() {
        TimelineResult result = simulator.simulate(
            player(5f, MitigationSnapshot.none(), DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.generic())),
            new ThreatTimeline(List.of(event("pop", 10f, 0), event("followup", 10f, 21)))
        );

        assertEquals(1, result.consumedDeathProtectionCount());
        assertFalse(result.survived());
        assertEquals("followup", result.firstLethalEventId().orElseThrow());
        assertEquals(2, result.eventResults().size());
    }

    @Test
    void sameMomentSmallerHitIsRejectedByCarriedLastHurt() {
        TimelineResult result = simulator.simulate(
            player(20f, MitigationSnapshot.none(), DeathProtectionSnapshot.none()),
            new ThreatTimeline(List.of(event("large", 12f, 0), event("small", 4f, 0)))
        );

        assertTrue(result.eventResult("small").damageResult().rejected());
        assertEquals(8f, result.finalHealth(), 0.0001f);
    }

    @Test
    void armorBreakFromFirstEventChangesSecondEventMitigation() {
        ArmorPieceSnapshot chest = new ArmorPieceSnapshot(CHEST, 8f, 2f, 0, 3, true);
        MitigationSnapshot mitigation = new MitigationSnapshot(8f, 2f, 1f, 0, false, 0, List.of(chest));

        TimelineResult result = simulator.simulate(
            player(20f, mitigation, DeathProtectionSnapshot.none()),
            new ThreatTimeline(List.of(event("one", 8f, 0), event("two", 8f, 21)))
        );

        assertTrue(result.eventResult("two").finalDamage() > result.eventResult("one").finalDamage());
        assertEquals(0, result.eventResult("two").damageResult().after().mitigation().armorPieces().getFirst().remainingDurability());
    }

    private static ThreatEvent event(String id, float raw, long tick) {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(raw), Set.of(), false, 1f, false, Optional.empty(), "test:" + id
        );
        return new ThreatEvent(
            id, ThreatKind.OTHER, new TickWindow(tick, tick), source, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, true, true, false
        );
    }

    private static PlayerSnapshot player(float health, MitigationSnapshot mitigation, DeathProtectionSnapshot protection) {
        return new PlayerSnapshot(
            health, 0f, false, false, false, DifficultySnapshot.NORMAL,
            mitigation, StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(), protection,
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }
}
