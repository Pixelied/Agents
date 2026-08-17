package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.DifficultySnapshot;
import dev.pixelied.survival.core.PlayerSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.pixelied.survival.damage.ArmorPieceSnapshot.Slot.CHEST;
import static dev.pixelied.survival.damage.DamageFlag.BYPASSES_ARMOR;
import static dev.pixelied.survival.damage.DamageFlag.BYPASSES_EFFECTS;
import static dev.pixelied.survival.damage.DamageFlag.DAMAGES_HELMET;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageSimulatorMitigationTest {
    private final DamageSimulator simulator = new DamageSimulator();

    @Test
    void resistanceThreeReducesBySixtyPercent() {
        DamageResult result = simulator.simulate(
            player(20f, 0f, MitigationSnapshot.none(), new StatusEffectsSnapshot(false, 2), DeathProtectionSnapshot.none(), HurtState.unknown()),
            source(10f)
        );

        assertEquals(4f, result.trace().after(DamageStage.RESISTANCE), 0.0001f);
        assertEquals(16f, result.after().health(), 0.0001f);
    }

    @Test
    void bypassEffectsSkipsResistanceAndProtection() {
        MitigationSnapshot mitigation = new MitigationSnapshot(0f, 0f, 1f, 20, false, 0);
        DamageResult result = simulator.simulate(
            player(20f, 0f, mitigation, new StatusEffectsSnapshot(false, 4), DeathProtectionSnapshot.none(), HurtState.unknown()),
            source(10f, BYPASSES_EFFECTS)
        );

        assertEquals(10f, result.trace().after(DamageStage.RESISTANCE), 0.0001f);
        assertEquals(10f, result.trace().after(DamageStage.MAGIC), 0.0001f);
    }

    @Test
    void protectionTwentyReducesRemainingDamageByEightyPercent() {
        MitigationSnapshot mitigation = new MitigationSnapshot(0f, 0f, 1f, 20, false, 0);
        DamageResult result = simulator.simulate(
            player(20f, 0f, mitigation, StatusEffectsSnapshot.none(), DeathProtectionSnapshot.none(), HurtState.unknown()),
            source(10f)
        );

        assertEquals(2f, result.trace().after(DamageStage.MAGIC), 0.0001f);
    }

    @Test
    void absorptionIsConsumedBeforeHealth() {
        DamageResult result = simulator.simulate(
            player(10f, 4f, MitigationSnapshot.none(), StatusEffectsSnapshot.none(), DeathProtectionSnapshot.none(), HurtState.unknown()),
            source(6f)
        );

        assertEquals(2f, result.trace().after(DamageStage.ABSORPTION), 0.0001f);
        assertEquals(0f, result.after().absorption(), 0.0001f);
        assertEquals(8f, result.after().health(), 0.0001f);
    }

    @Test
    void armorAndToughnessUseVanillaFormula() {
        MitigationSnapshot mitigation = new MitigationSnapshot(20f, 8f, 1f, 0, false, 0);
        DamageResult result = simulator.simulate(
            player(20f, 0f, mitigation, StatusEffectsSnapshot.none(), DeathProtectionSnapshot.none(), HurtState.unknown()),
            source(10f)
        );

        assertEquals(3f, result.trace().after(DamageStage.ARMOR), 0.0001f);
    }

    @Test
    void armorThatSurvivesFirstHitButBreaksOnSecondStopsMitigatingSecondHit() {
        ArmorPieceSnapshot chest = new ArmorPieceSnapshot(CHEST, 8f, 2f, 0, 3, true);
        MitigationSnapshot mitigation = new MitigationSnapshot(8f, 2f, 1f, 0, false, 0, List.of(chest));
        DamageSourceSnapshot hit = source(8f);

        DamageResult first = simulator.simulate(
            player(20f, 0f, mitigation, StatusEffectsSnapshot.none(), DeathProtectionSnapshot.none(), HurtState.unknown()),
            hit
        );
        PlayerSnapshot afterCooldown = withHurtState(first.after(), new HurtState(DamageRange.exact(0f), 0, Confidence.EXACT));
        DamageResult second = simulator.simulate(afterCooldown, hit);

        assertEquals(1, first.after().mitigation().armorPieces().getFirst().remainingDurability());
        assertEquals(0, second.after().mitigation().armorPieces().getFirst().remainingDurability());
        assertTrue(second.trace().after(DamageStage.HEALTH_DAMAGE) > first.trace().after(DamageStage.HEALTH_DAMAGE));
    }

    @Test
    void bypassArmorSkipsBothDurabilityDamageAndReduction() {
        ArmorPieceSnapshot chest = new ArmorPieceSnapshot(CHEST, 8f, 2f, 0, 3, true);
        MitigationSnapshot mitigation = new MitigationSnapshot(8f, 2f, 1f, 0, false, 0, List.of(chest));

        DamageResult result = simulator.simulate(
            player(20f, 0f, mitigation, StatusEffectsSnapshot.none(), DeathProtectionSnapshot.none(), HurtState.unknown()),
            source(8f, BYPASSES_ARMOR)
        );

        assertEquals(8f, result.trace().after(DamageStage.ARMOR), 0.0001f);
        assertEquals(3, result.after().mitigation().armorPieces().getFirst().remainingDurability());
    }

    @Test
    void helmetBreakStillReducesTheCurrentHelmetDamageHit() {
        MitigationSnapshot mitigation = new MitigationSnapshot(0f, 0f, 1f, 0, true, 1);
        DamageResult result = simulator.simulate(
            player(20f, 0f, mitigation, StatusEffectsSnapshot.none(), DeathProtectionSnapshot.none(), HurtState.unknown()),
            source(8f, DAMAGES_HELMET)
        );

        assertEquals(6f, result.trace().after(DamageStage.HELMET), 0.0001f);
        assertFalse(result.after().mitigation().helmetPresent());
        assertEquals(0, result.after().mitigation().helmetDurability());
    }

    @Test
    void simulationPreservesPlayerStateProperties() {
        PlayerSnapshot stateful = new PlayerSnapshot(
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
            Map.of(),
            Map.of("remaining_fire_ticks", "22")
        );

        DamageResult result = simulator.simulate(stateful, source(1f));

        assertEquals("22", result.after().state("remaining_fire_ticks"));
    }

    private static DamageSourceSnapshot source(float raw, DamageFlag... flags) {
        return new DamageSourceSnapshot(DamageRange.exact(raw), Set.of(flags), false, 1f, false, Optional.empty(), "test");
    }

    static PlayerSnapshot player(
        float health,
        float absorption,
        MitigationSnapshot mitigation,
        StatusEffectsSnapshot effects,
        DeathProtectionSnapshot protection,
        HurtState hurtState
    ) {
        return new PlayerSnapshot(
            health,
            absorption,
            false,
            false,
            false,
            DifficultySnapshot.NORMAL,
            mitigation,
            effects,
            BlockingSnapshot.none(),
            hurtState,
            protection,
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6),
            new Vec3Snapshot(0, 0, 0),
            new Vec3Snapshot(0, 0, 0),
            Map.of()
        );
    }

    private static PlayerSnapshot withHurtState(PlayerSnapshot player, HurtState hurtState) {
        return new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(), player.deadOrDying(),
            player.difficulty(), player.mitigation(), player.statusEffects(), player.blocking(), hurtState, player.deathProtection(),
            player.boundingBox(), player.position(), player.velocity(), player.equipmentItemKeys(), player.stateProperties()
        );
    }
}
