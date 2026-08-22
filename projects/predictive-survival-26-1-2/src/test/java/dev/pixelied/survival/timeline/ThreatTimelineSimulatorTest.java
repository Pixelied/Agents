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
    void uncertainDeathProtectionPopDoesNotCountAsGuaranteedSurvival() {
        TimelineResult result = simulator.simulate(
            player(5f, MitigationSnapshot.none(), DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.generic())),
            new ThreatTimeline(List.of(event("uncertain-pop", 10f, 0)))
        );

        assertEquals(1, result.consumedDeathProtectionCount());
        assertEquals(1f, result.finalHealth(), 0.0001f);
        assertFalse(result.survived());
    }

    @Test
    void simulationContinuesAfterPopAndCanStillDie() {
        TimelineResult result = simulator.simulate(
            player(5f, MitigationSnapshot.none(), DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.deterministicNoOp())),
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
    void rejectedPrerequisiteHitSuppressesDependentThreat() {
        PlayerSnapshot start = playerWithHurtState(20f, new HurtState(DamageRange.exact(5f), 20, Confidence.EXACT));
        ThreatEvent direct = event("direct", 4f, 0);
        ThreatEvent dependent = dependentEvent("dependent", 3f, 1, "direct");

        TimelineResult result = simulator.simulate(start, new ThreatTimeline(List.of(direct, dependent)));

        assertTrue(result.eventResult("direct").damageResult().rejected());
        assertEquals(1, result.eventResults().size());
        assertEquals(20f, result.finalHealth(), 0.0001f);
    }

    @Test
    void acceptedPrerequisiteHitEnablesDependentThreat() {
        PlayerSnapshot start = playerWithHurtState(20f, new HurtState(DamageRange.exact(5f), 20, Confidence.EXACT));
        ThreatEvent direct = event("direct", 6f, 0);
        ThreatEvent dependent = dependentEvent("dependent", 3f, 1, "direct");

        TimelineResult result = simulator.simulate(start, new ThreatTimeline(List.of(direct, dependent)));

        assertFalse(result.eventResult("direct").damageResult().rejected());
        assertFalse(result.eventResult("dependent").damageResult().rejected());
        assertEquals(2, result.eventResults().size());
        assertEquals(16f, result.finalHealth(), 0.0001f);
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

    @Test
    void finiteFireResistanceExpiresBeforeDelayedThreat() {
        StatusEffectsSnapshot effects = new StatusEffectsSnapshot(
            true,
            -1,
            Map.of("minecraft:fire_resistance", new EffectInstanceSnapshot("minecraft:fire_resistance", 1, 0))
        );
        PlayerSnapshot start = player(20f, MitigationSnapshot.none(), DeathProtectionSnapshot.none(), effects, 0f);
        DamageSourceSnapshot fire = new DamageSourceSnapshot(
            DamageRange.exact(5f), Set.of(DamageFlag.IS_FIRE, DamageFlag.BYPASSES_COOLDOWN),
            false, 1f, false, Optional.empty(), "test:fire"
        );

        TimelineResult result = simulator.simulate(
            start,
            new ThreatTimeline(List.of(new ThreatEvent(
                "fire", ThreatKind.OTHER, new TickWindow(5, 5), fire, Confidence.EXACT,
                Optional.empty(), Optional.empty(), true, false, true, false
            )))
        );

        assertEquals(15f, result.finalHealth(), 0.0001f, "expired fire resistance must not protect a later hit");
    }

    @Test
    void absorptionFromExpiredEffectDoesNotPersistForever() {
        StatusEffectsSnapshot effects = new StatusEffectsSnapshot(
            false,
            -1,
            Map.of("minecraft:absorption", new EffectInstanceSnapshot("minecraft:absorption", 1, 0))
        );
        PlayerSnapshot start = player(20f, MitigationSnapshot.none(), DeathProtectionSnapshot.none(), effects, 4f);

        TimelineResult result = simulator.simulate(
            start,
            new ThreatTimeline(List.of(event("delayed", 5f, 5)))
        );

        assertEquals(15f, result.finalHealth(), 0.0001f, "expired absorption hearts must be removed before damage");
        assertEquals(0f, result.finalAbsorption(), 0.0001f);
    }

    @Test
    void regenerationUsesVanillaRemainingDurationPhase() {
        StatusEffectsSnapshot effects = new StatusEffectsSnapshot(
            false,
            -1,
            Map.of("minecraft:regeneration", new EffectInstanceSnapshot("minecraft:regeneration", 900, 1))
        );
        PlayerSnapshot start = new PlayerSnapshot(
            1f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), effects, BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of(),
            Map.of("max_health", "20.0")
        );

        TimelineResult result = simulator.simulate(
            start,
            new ThreatTimeline(List.of(event("phase-marker", 0f, 26)))
        );

        assertEquals(3f, result.finalHealth(), 0.0001f,
            "Regeneration II at duration 900 must heal at remaining durations 900 and 875 before tick 26");
    }

    @Test
    void vanillaTotemRegenerationPreventsFalseDeathOnLateWitherTail() {
        PlayerSnapshot start = new PlayerSnapshot(
            4f, 0f, false, false, false, DifficultySnapshot.EASY,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.mainHand(DeathProtectionSnapshot.ProtectionItem.vanillaTotem()),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of(),
            Map.of("max_health", "20.0")
        );
        DamageSourceSnapshot directDamage = new DamageSourceSnapshot(
            DamageRange.exact(8f), Set.of(), true, 1f, false, Optional.empty(), "minecraft:mob_attack"
        );
        ThreatEvent direct = new ThreatEvent(
            "melee", ThreatKind.MELEE, new TickWindow(0, 0), directDamage, Confidence.POTENTIAL,
            Optional.empty(), Optional.empty(), true, true, true, false
        );
        DamageSourceSnapshot witherDamage = new DamageSourceSnapshot(
            DamageRange.exact(1f), Set.of(DamageFlag.BYPASSES_ARMOR), false, 1f, false,
            Optional.empty(), "minecraft:wither"
        );
        ThreatTimeline timeline = new ThreatTimeline(List.of(
            direct,
            dependent("wither-0", witherDamage, 1, "melee"),
            dependent("wither-1", witherDamage, 41, "melee"),
            dependent("wither-2", witherDamage, 81, "melee"),
            dependent("wither-3", witherDamage, 121, "melee")
        ));

        TimelineResult result = simulator.simulate(start, timeline);

        assertTrue(result.survived(), "vanilla Totem Regeneration II must be modeled before the late Wither tail");
        assertEquals(1, result.consumedDeathProtectionCount());
        assertTrue(result.finalHealth() > 0f);
    }

    @Test
    void elapsedTimelineTicksPreservePlayerStateProperties() {
        PlayerSnapshot start = new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of(),
            Map.of("food_tick_timer", "79", "remaining_fire_ticks", "3")
        );

        TimelineResult result = simulator.simulate(
            start,
            new ThreatTimeline(List.of(event("delayed", 1f, 5)))
        );

        assertEquals("79", result.eventResult("delayed").damageResult().after().state("food_tick_timer"));
        assertEquals("3", result.eventResult("delayed").damageResult().after().state("remaining_fire_ticks"));
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

    private static ThreatEvent dependentEvent(String id, float raw, long tick, String prerequisiteId) {
        DamageSourceSnapshot source = new DamageSourceSnapshot(
            DamageRange.exact(raw), Set.of(DamageFlag.BYPASSES_COOLDOWN), false, 1f, false, Optional.empty(), "test:" + id
        );
        return dependent(id, source, tick, prerequisiteId);
    }

    private static ThreatEvent dependent(String id, DamageSourceSnapshot source, long tick, String prerequisiteId) {
        return new ThreatEvent(
            id, ThreatKind.OTHER, new TickWindow(tick, tick), source, Confidence.EXACT,
            Optional.empty(), Optional.empty(), true, true, true, false, Optional.of(prerequisiteId)
        );
    }

    private static PlayerSnapshot player(float health, MitigationSnapshot mitigation, DeathProtectionSnapshot protection) {
        return player(health, mitigation, protection, StatusEffectsSnapshot.none(), 0f);
    }

    private static PlayerSnapshot player(
        float health,
        MitigationSnapshot mitigation,
        DeathProtectionSnapshot protection,
        StatusEffectsSnapshot effects,
        float absorption
    ) {
        return new PlayerSnapshot(
            health, absorption, false, false, false, DifficultySnapshot.NORMAL,
            mitigation, effects, BlockingSnapshot.none(), HurtState.unknown(), protection,
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }

    private static PlayerSnapshot playerWithHurtState(float health, HurtState hurtState) {
        return new PlayerSnapshot(
            health, 0f, false, false, false, DifficultySnapshot.NORMAL,
            MitigationSnapshot.none(), StatusEffectsSnapshot.none(), BlockingSnapshot.none(), hurtState,
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }
}
