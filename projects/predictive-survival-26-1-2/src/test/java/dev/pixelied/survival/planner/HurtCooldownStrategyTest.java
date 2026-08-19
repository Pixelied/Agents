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
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSimulator;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.damage.DamageStage;
import dev.pixelied.survival.damage.DeathProtectionSnapshot;
import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MitigationSnapshot;
import dev.pixelied.survival.damage.StatusEffectsSnapshot;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class HurtCooldownStrategyTest {
    private final HurtCooldownStrategy strategy = new HurtCooldownStrategy();

    @Test
    void oneDamagePrecursorDoesNotCancelTwentyDamageHit() {
        DamageSimulator simulator = new DamageSimulator();
        PlayerSnapshot start = player(40f, MitigationSnapshot.none(), HurtState.unknown());
        var afterPrecursor = simulator.simulate(start, damage(1f, Set.of(), "test:precursor")).after();
        var incoming = simulator.simulate(afterPrecursor, damage(20f, Set.of(), "test:incoming"));

        assertEquals(19f, incoming.trace().after(DamageStage.HURT_COOLDOWN), 0.0001f);
    }

    @Test
    void unknownServerLastHurtRejectsIntentionalDamage() {
        HurtCooldownCandidate candidate = candidate(true);
        PredictionContext context = context(player(20f, armor(), HurtState.unknown()));

        assertTrue(strategy.evaluate(candidate, context, lethalBypassArmorTimeline()).isEmpty());
    }

    @Test
    void runtimeValidationIsRequiredForEveryIntentionalDamageStrategy() {
        HurtCooldownCandidate candidate = candidate(false);
        PredictionContext context = context(player(20f, armor(), trustedNoCooldown()));

        assertTrue(strategy.evaluate(candidate, context, lethalBypassArmorTimeline()).isEmpty());
    }

    @Test
    void experimentalCandidateMustBeatNoActionWorstCase() {
        HurtCooldownCandidate candidate = candidate(true);
        PredictionContext context = context(player(20f, armor(), trustedNoCooldown()));

        ActionSimulation simulation = strategy.evaluate(candidate, context, lethalBypassArmorTimeline()).orElseThrow();
        ActionSimulation baseline = new SurvivalPlanner().simulate(
            context,
            lethalBypassArmorTimeline(),
            new SurvivalAction.NoAction(),
            SafetyMode.EXPERIMENTAL
        );

        assertTrue(simulation.result().survived());
        assertTrue(simulation.result().finalHealth() > baseline.result().finalHealth());
    }

    @Test
    void uncertainPrecursorTimingIsRejected() {
        ThreatEvent uncertain = event(
            "precursor",
            new TickWindow(0, 1),
            10f,
            Set.of(),
            Confidence.BOUNDED
        );
        HurtCooldownCandidate candidate = new HurtCooldownCandidate(
            "uncertain",
            uncertain,
            new SurvivalAction.DeliberateDamage(0, true, true, 1d, 0, 1),
            true
        );

        assertTrue(strategy.evaluate(candidate, context(player(20f, armor(), trustedNoCooldown())), lethalBypassArmorTimeline()).isEmpty());
    }

    private static HurtCooldownCandidate candidate(boolean runtimeValidated) {
        ThreatEvent precursor = event(
            "precursor",
            new TickWindow(0, 0),
            10f,
            Set.of(),
            Confidence.EXACT
        );
        return new HurtCooldownCandidate(
            "armor-softened-precursor",
            precursor,
            new SurvivalAction.DeliberateDamage(0, true, true, 1d, 0, 1),
            runtimeValidated
        );
    }

    private static ThreatTimeline lethalBypassArmorTimeline() {
        return new ThreatTimeline(List.of(event(
            "incoming",
            new TickWindow(1, 1),
            20f,
            Set.of(DamageFlag.BYPASSES_ARMOR),
            Confidence.EXACT
        )));
    }

    private static ThreatEvent event(
        String id,
        TickWindow impact,
        float raw,
        Set<DamageFlag> flags,
        Confidence confidence
    ) {
        return new ThreatEvent(
            id,
            ThreatKind.OTHER,
            impact,
            damage(raw, flags, "test:" + id),
            confidence,
            Optional.empty(),
            Optional.empty(),
            false,
            false,
            false,
            false
        );
    }

    private static DamageSourceSnapshot damage(float raw, Set<DamageFlag> flags, String key) {
        return new DamageSourceSnapshot(
            DamageRange.exact(raw), flags, false, 1f, false, Optional.empty(), key
        );
    }

    private static MitigationSnapshot armor() {
        return new MitigationSnapshot(20f, 8f, 1f, 0, false, 0);
    }

    private static HurtState trustedNoCooldown() {
        return new HurtState(DamageRange.exact(0f), 0, Confidence.EXACT);
    }

    private static PredictionContext context(PlayerSnapshot player) {
        return new PredictionContext(
            player,
            WorldSnapshot.empty(),
            new TimingSnapshot(0, 50, 0, new TickWindow(0, 0)),
            EngineLimits.defaults()
        );
    }

    private static PlayerSnapshot player(float health, MitigationSnapshot mitigation, HurtState hurtState) {
        return new PlayerSnapshot(
            health, 0f, false, false, false, DifficultySnapshot.NORMAL,
            mitigation, StatusEffectsSnapshot.none(), BlockingSnapshot.none(), hurtState,
            DeathProtectionSnapshot.none(), new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }
}
