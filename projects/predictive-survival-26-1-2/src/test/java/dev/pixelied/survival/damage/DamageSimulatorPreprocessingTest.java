package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.pixelied.survival.damage.DamageFlag.BYPASSES_COOLDOWN;
import static dev.pixelied.survival.damage.DamageFlag.DAMAGES_HELMET;
import static dev.pixelied.survival.damage.DamageFlag.IS_FIRE;
import static dev.pixelied.survival.damage.DamageFlag.IS_FREEZING;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageSimulatorPreprocessingTest {
    private final DamageSimulator simulator = new DamageSimulator();

    @Test
    void easyDifficultyUsesVanillaFormula() {
        DamageResult result = simulator.simulate(
            player(DifficultySnapshot.EASY, StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(), MitigationSnapshot.none()),
            source(10f, true, 1f)
        );

        assertEquals(6f, result.trace().after(DamageStage.DIFFICULTY), 0.0001f);
    }

    @Test
    void fireResistanceRejectsBeforeHurtCooldown() {
        HurtState initial = new HurtState(DamageRange.exact(4f), 14, Confidence.EXACT);
        DamageResult result = simulator.simulate(
            player(DifficultySnapshot.NORMAL, new StatusEffectsSnapshot(true, -1), BlockingSnapshot.none(), initial, MitigationSnapshot.none()),
            source(8f, false, 1f, IS_FIRE)
        );

        assertTrue(result.rejected());
        assertEquals(initial, result.after().hurtState());
    }

    @Test
    void largerHitDuringStrongCooldownAppliesOnlyExcess() {
        DamageResult result = simulator.simulate(
            player(DifficultySnapshot.NORMAL, StatusEffectsSnapshot.none(), BlockingSnapshot.none(),
                new HurtState(DamageRange.exact(5f), 15, Confidence.EXACT), MitigationSnapshot.none()),
            source(12f, false, 1f)
        );

        assertEquals(7f, result.trace().after(DamageStage.HURT_COOLDOWN), 0.0001f);
        assertEquals(12f, result.after().hurtState().lastHurt().max(), 0.0001f);
        assertEquals(15, result.after().hurtState().invulnerableTime());
    }

    @Test
    void fullyBlockedHitLeavesZeroLastHurt() {
        DamageResult result = simulator.simulate(
            player(DifficultySnapshot.NORMAL, StatusEffectsSnapshot.none(), new BlockingSnapshot(true, 1f, 5, 5),
                HurtState.unknown(), MitigationSnapshot.none()),
            source(8f, false, 1f)
        );

        assertEquals(0f, result.trace().after(DamageStage.BLOCKING), 0.0001f);
        assertTrue(result.rejected(), "vanilla hurtServer returns false for a fully blocked hit");
        assertEquals(0f, result.after().hurtState().lastHurt().max(), 0.0001f);
        assertEquals(20, result.after().hurtState().invulnerableTime());
    }

    @Test
    void bypassCooldownProcessesFullIncomingDamage() {
        DamageResult result = simulator.simulate(
            player(DifficultySnapshot.NORMAL, StatusEffectsSnapshot.none(), BlockingSnapshot.none(),
                new HurtState(DamageRange.exact(5f), 15, Confidence.EXACT), MitigationSnapshot.none()),
            source(12f, false, 1f, BYPASSES_COOLDOWN)
        );

        assertEquals(12f, result.trace().after(DamageStage.HURT_COOLDOWN), 0.0001f);
        assertEquals(12f, result.after().hurtState().lastHurt().max(), 0.0001f);
        assertEquals(20, result.after().hurtState().invulnerableTime());
    }

    @Test
    void freezingMultiplierPrecedesHelmetReduction() {
        DamageResult result = simulator.simulate(
            player(DifficultySnapshot.NORMAL, StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(),
                new MitigationSnapshot(0f, 0f, 1f, 0, true, 10)),
            source(8f, false, 5f, IS_FREEZING, DAMAGES_HELMET)
        );

        assertEquals(40f, result.trace().after(DamageStage.FREEZING), 0.0001f);
        assertEquals(30f, result.trace().after(DamageStage.HELMET), 0.0001f);
    }

    @Test
    void nonFiniteDamageSanitizesToFloatMaxValue() {
        DamageResult result = simulator.simulate(
            player(DifficultySnapshot.NORMAL, StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(), MitigationSnapshot.none()),
            source(Float.POSITIVE_INFINITY, false, 1f)
        );

        assertEquals(Float.MAX_VALUE, result.trace().after(DamageStage.SANITIZE));
    }

    private static DamageSourceSnapshot source(float raw, boolean scalesWithDifficulty, float freezingMultiplier, DamageFlag... flags) {
        return new DamageSourceSnapshot(
            DamageRange.exact(raw),
            Set.of(flags),
            scalesWithDifficulty,
            freezingMultiplier,
            false,
            Optional.empty(),
            "test"
        );
    }

    private static PlayerSnapshot player(
        DifficultySnapshot difficulty,
        StatusEffectsSnapshot effects,
        BlockingSnapshot blocking,
        HurtState hurtState,
        MitigationSnapshot mitigation
    ) {
        return new PlayerSnapshot(
            20f,
            0f,
            false,
            false,
            false,
            difficulty,
            mitigation,
            effects,
            blocking,
            hurtState,
            DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
    }
}
