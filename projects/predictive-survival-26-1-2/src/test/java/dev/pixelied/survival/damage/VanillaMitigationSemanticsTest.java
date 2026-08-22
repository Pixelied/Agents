package dev.pixelied.survival.damage;

import dev.pixelied.survival.core.AabbSnapshot;
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
import static dev.pixelied.survival.damage.ArmorPieceSnapshot.Slot.FEET;
import static dev.pixelied.survival.damage.DamageFlag.BURN_FROM_STEPPING;
import static dev.pixelied.survival.damage.DamageFlag.BYPASSES_INVULNERABILITY;
import static dev.pixelied.survival.damage.DamageFlag.IS_EXPLOSION;
import static dev.pixelied.survival.damage.DamageFlag.IS_FALL;
import static dev.pixelied.survival.damage.DamageFlag.IS_FIRE;
import static dev.pixelied.survival.damage.DamageFlag.IS_PROJECTILE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaMitigationSemanticsTest {
    private final DamageSimulator simulator = new DamageSimulator();

    @Test
    void protectionUsesDamageSourceSpecificVanillaEffects() {
        assertMagicDamage(8.4f, protectionPiece(CHEST, new ProtectionEnchantmentsSnapshot(4, 0, 0, 0, 0, 0)), source(10f));
        assertMagicDamage(6.8f, protectionPiece(CHEST, new ProtectionEnchantmentsSnapshot(0, 4, 0, 0, 0, 0)), source(10f, IS_EXPLOSION));
        assertMagicDamage(10f, protectionPiece(CHEST, new ProtectionEnchantmentsSnapshot(0, 4, 0, 0, 0, 0)), source(10f));
        assertMagicDamage(6.8f, protectionPiece(CHEST, new ProtectionEnchantmentsSnapshot(0, 0, 4, 0, 0, 0)), source(10f, IS_PROJECTILE));
        assertMagicDamage(6.8f, protectionPiece(CHEST, new ProtectionEnchantmentsSnapshot(0, 0, 0, 4, 0, 0)), source(10f, IS_FIRE));
        assertMagicDamage(5.2f, protectionPiece(FEET, new ProtectionEnchantmentsSnapshot(0, 0, 0, 0, 4, 0)), source(10f, IS_FALL));
    }

    @Test
    void protectionEffectsRequireSourceNotToBypassInvulnerability() {
        ArmorPieceSnapshot piece = protectionPiece(CHEST, new ProtectionEnchantmentsSnapshot(4, 4, 4, 4, 0, 0));
        DamageResult result = simulator.simulate(player(mitigation(piece)), source(10f, IS_EXPLOSION, BYPASSES_INVULNERABILITY));
        assertEquals(10f, result.trace().after(DamageStage.MAGIC), 0.0001f);
    }

    @Test
    void frostWalkerImmunityMatchesBurnFromSteppingPredicate() {
        ArmorPieceSnapshot boots = protectionPiece(FEET, new ProtectionEnchantmentsSnapshot(0, 0, 0, 0, 0, 2));

        DamageResult hotFloor = simulator.simulate(player(mitigation(boots)), source(4f, BURN_FROM_STEPPING));
        DamageResult bypassing = simulator.simulate(player(mitigation(boots)), source(4f, BURN_FROM_STEPPING, BYPASSES_INVULNERABILITY));

        assertTrue(hotFloor.rejected());
        assertEquals(20f, hotFloor.after().health(), 0.0001f);
        assertFalse(bypassing.rejected());
        assertEquals(16f, bypassing.after().health(), 0.0001f);
    }

    @Test
    void breachModifiesArmorFractionFromDamageSourceWeapon() {
        MitigationSnapshot armor = new MitigationSnapshot(20f, 8f, false, 0, List.of());
        DamageSourceSnapshot breachFour = sourceWithArmorAdjustment(10f, -0.60f);

        DamageResult result = simulator.simulate(player(armor), breachFour);

        assertEquals(9f, result.trace().after(DamageStage.ARMOR), 0.0001f);
    }

    @Test
    void brokenArmorStopsContributingItsProtectionToLaterHits() {
        ArmorPieceSnapshot chest = new ArmorPieceSnapshot(
            CHEST,
            0f,
            0f,
            new ProtectionEnchantmentsSnapshot(0, 4, 0, 0, 0, 0),
            3,
            true,
            Set.of()
        );
        DamageSourceSnapshot explosion = source(8f, IS_EXPLOSION);

        DamageResult first = simulator.simulate(player(mitigation(chest)), explosion);
        PlayerSnapshot resetCooldown = withNoCooldown(first.after());
        DamageResult second = simulator.simulate(resetCooldown, explosion);

        assertEquals(1, first.after().mitigation().armorPieces().getFirst().remainingDurability());
        assertEquals(0, second.after().mitigation().armorPieces().getFirst().remainingDurability());
        assertTrue(second.trace().after(DamageStage.MAGIC) > first.trace().after(DamageStage.MAGIC));
    }

    @Test
    void damageResistantArmorDoesNotLoseDurabilityToMatchingSource() {
        ArmorPieceSnapshot piece = new ArmorPieceSnapshot(
            CHEST,
            8f,
            2f,
            ProtectionEnchantmentsSnapshot.none(),
            4,
            true,
            Set.of("minecraft:in_fire")
        );
        DamageSourceSnapshot fire = new DamageSourceSnapshot(
            DamageRange.exact(8f), Set.of(IS_FIRE), false, 1f, false, Optional.empty(), "minecraft:in_fire", 0f, 0f
        );

        DamageResult result = simulator.simulate(player(mitigation(piece)), fire);

        assertEquals(4, result.after().mitigation().armorPieces().getFirst().remainingDurability());
    }

    private void assertMagicDamage(float expected, ArmorPieceSnapshot piece, DamageSourceSnapshot source) {
        DamageResult result = simulator.simulate(player(mitigation(piece)), source);
        assertEquals(expected, result.trace().after(DamageStage.MAGIC), 0.0001f);
    }

    private static MitigationSnapshot mitigation(ArmorPieceSnapshot piece) {
        return new MitigationSnapshot(piece.armor(), piece.toughness(), piece.slot() == ArmorPieceSnapshot.Slot.HEAD, piece.remainingDurability(), List.of(piece));
    }

    private static ArmorPieceSnapshot protectionPiece(ArmorPieceSnapshot.Slot slot, ProtectionEnchantmentsSnapshot enchantments) {
        return new ArmorPieceSnapshot(slot, 0f, 0f, enchantments, 100, true, Set.of());
    }

    private static DamageSourceSnapshot source(float raw, DamageFlag... flags) {
        return new DamageSourceSnapshot(DamageRange.exact(raw), Set.of(flags), false, 1f, false, Optional.empty(), "test", 0f, 0f);
    }

    private static DamageSourceSnapshot sourceWithArmorAdjustment(float raw, float adjustment) {
        return new DamageSourceSnapshot(DamageRange.exact(raw), Set.of(), false, 1f, false, Optional.empty(), "test", 0f, adjustment);
    }

    private static PlayerSnapshot player(MitigationSnapshot mitigation) {
        return new PlayerSnapshot(
            20f, 0f, false, false, false, DifficultySnapshot.NORMAL,
            mitigation, StatusEffectsSnapshot.none(), BlockingSnapshot.none(), HurtState.unknown(), DeathProtectionSnapshot.none(),
            new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6), new Vec3Snapshot(0, 0, 0), new Vec3Snapshot(0, 0, 0), Map.of()
        );
    }

    private static PlayerSnapshot withNoCooldown(PlayerSnapshot player) {
        return new PlayerSnapshot(
            player.health(), player.absorption(), player.playerInvulnerable(), player.abilityInvulnerable(), player.deadOrDying(),
            player.difficulty(), player.mitigation(), player.statusEffects(), player.blocking(), HurtState.unknown(), player.deathProtection(),
            player.boundingBox(), player.position(), player.velocity(), player.equipmentItemKeys(), player.stateProperties()
        );
    }
}
