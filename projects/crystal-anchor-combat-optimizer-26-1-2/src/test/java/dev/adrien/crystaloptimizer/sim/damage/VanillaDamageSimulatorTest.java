package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.ArmorPieceState;
import dev.adrien.crystaloptimizer.sim.model.BlockingState;
import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.HurtWindowState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaDamageSimulatorTest {
    @Test
    void armorThatBreaksStillMitigatesTheSameHitButIsAbsentAfterward() {
        var chest = ArmorPieceState.testPiece(8.0f, 3.0f, 1, 0.0f);
        var target = SimCombatant.testPlayer(20.0f).withChest(chest);
        var request = DamageRequest.explosion(16.0f);

        var result = VanillaDamageSimulator.apply(target, request);
        float expectedSameHitArmor = VanillaMitigationPipeline.afterArmor(
            result.trace().acceptedIncoming(),
            target.equipment(),
            request
        );

        assertTrue(result.trace().brokenSlots().contains(EquipmentSlot.CHEST));
        assertEquals(expectedSameHitArmor, result.trace().postArmor(), 0.0001f);
        assertTrue(result.trace().postArmor() < result.trace().acceptedIncoming());
        assertEquals(0.0f, result.target().equipment().armorPoints(), 0.0001f);

        var next = VanillaDamageSimulator.apply(
            result.target().withHurtWindow(new HurtWindowState(0, 0.0f)),
            request
        );
        assertEquals(16.0f, next.trace().postArmor(), 0.0001f);
    }

    @Test
    void enchantmentOnArmorThatBreaksDoesNotProtectTheSameHitAfterDurabilityBreak() {
        var chest = ArmorPieceState.testPiece(8.0f, 3.0f, 1, 8.0f);
        var target = SimCombatant.testPlayer(20.0f).withChest(chest);

        var result = VanillaDamageSimulator.apply(target, DamageRequest.explosion(16.0f));

        assertTrue(result.trace().brokenSlots().contains(EquipmentSlot.CHEST));
        assertEquals(result.trace().postArmor(), result.trace().postMagic(), 0.0001f);
    }

    @Test
    void totemSetsOneHealthEightAbsorptionAndPreservesHurtWindow() {
        var target = SimCombatant.testPlayer(6.0f)
            .withTotem(TotemState.OFFHAND)
            .withEffects(EffectState.resistance(0, 200));

        var result = VanillaDamageSimulator.apply(target, DamageRequest.explosion(40.0f));

        assertTrue(result.trace().totemTriggered());
        assertEquals(1.0f, result.target().health(), 0.0001f);
        assertEquals(8.0f, result.target().absorption(), 0.0001f);
        assertFalse(result.target().effects().hasResistance());
        assertEquals(result.trace().incoming(), result.target().hurtWindow().lastHurt(), 0.0001f);
    }

    @Test
    void hardDifficultyScalesIncomingBeforeTheHurtWindow() {
        var result = VanillaDamageSimulator.apply(
            SimCombatant.testPlayer(20.0f),
            DamageRequest.explosion(10.0f).withDifficulty(Difficulty.HARD)
        );

        assertEquals(15.0f, result.trace().difficultyScaled(), 0.0001f);
        assertEquals(15.0f, result.trace().incoming(), 0.0001f);
    }

    @Test
    void easyDifficultyUsesVanillaHalfPlusOneScaling() {
        var result = VanillaDamageSimulator.apply(
            SimCombatant.testPlayer(20.0f),
            DamageRequest.explosion(10.0f).withDifficulty(Difficulty.EASY)
        );

        assertEquals(6.0f, result.trace().difficultyScaled(), 0.0001f);
        assertEquals(6.0f, result.trace().incoming(), 0.0001f);
    }

    @Test
    void peacefulDifficultyReturnsBeforeLivingEntityHurtStateMutates() {
        var target = SimCombatant.testPlayer(20.0f)
            .withHurtWindow(new HurtWindowState(7, 4.0f));

        var result = VanillaDamageSimulator.apply(
            target,
            DamageRequest.explosion(10.0f).withDifficulty(Difficulty.PEACEFUL)
        );

        assertFalse(result.accepted());
        assertEquals(0.0f, result.trace().difficultyScaled(), 0.0001f);
        assertEquals(target, result.target());
    }

    @Test
    void resistanceReducesPostArmorDamageBeforeEnchantProtection() {
        var target = SimCombatant.testPlayer(20.0f)
            .withEffects(EffectState.resistance(0, 200));

        var result = VanillaDamageSimulator.apply(target, DamageRequest.explosion(10.0f));

        assertEquals(8.0f, result.trace().postMagic(), 0.0001f);
        assertEquals(12.0f, result.target().health(), 0.0001f);
    }

    @Test
    void absorptionIsConsumedBeforeHealth() {
        var target = SimCombatant.testPlayer(20.0f).withAbsorption(5.0f);

        var result = VanillaDamageSimulator.apply(target, DamageRequest.explosion(10.0f));

        assertEquals(5.0f, result.trace().absorptionConsumed(), 0.0001f);
        assertEquals(5.0f, result.trace().healthDamage(), 0.0001f);
        assertEquals(0.0f, result.target().absorption(), 0.0001f);
        assertEquals(15.0f, result.target().health(), 0.0001f);
    }

    @Test
    void enchantmentProtectionUsesVanillaMagicProtectionClamp() {
        var chest = ArmorPieceState.testPiece(0.0f, 0.0f, 100, 10.0f);
        var target = SimCombatant.testPlayer(20.0f).withChest(chest);

        var result = VanillaDamageSimulator.apply(target, DamageRequest.explosion(10.0f));

        assertEquals(6.0f, result.trace().postMagic(), 0.0001f);
    }

    @Test
    void shieldBlocksFrontExplosionButNotRearExplosion() {
        var target = SimCombatant.testPlayer(20.0f)
            .withBlocking(BlockingState.shield(new Vec3(0.0, 0.0, 0.0), 0.0f));

        var front = VanillaDamageSimulator.apply(
            target,
            DamageRequest.explosion(10.0f).withSourcePosition(new Vec3(0.0, 0.0, 4.0))
        );
        var rear = VanillaDamageSimulator.apply(
            target,
            DamageRequest.explosion(10.0f).withSourcePosition(new Vec3(0.0, 0.0, -4.0))
        );

        assertEquals(10.0f, front.trace().blockedDamage(), 0.0001f);
        assertEquals(20.0f, front.target().health(), 0.0001f);
        assertEquals(0.0f, rear.trace().blockedDamage(), 0.0001f);
        assertEquals(10.0f, rear.target().health(), 0.0001f);
    }

    @Test
    void strongerSecondHitAfterTotemOnlyMitigatesTheHurtWindowDelta() {
        var first = VanillaDamageSimulator.apply(
            SimCombatant.testPlayer(5.0f).withTotem(TotemState.OFFHAND),
            DamageRequest.explosion(10.0f)
        );

        var second = VanillaDamageSimulator.apply(first.target(), DamageRequest.explosion(20.0f));

        assertTrue(first.trace().totemTriggered());
        assertEquals(10.0f, first.target().hurtWindow().lastHurt(), 0.0001f);
        assertEquals(10.0f, second.trace().acceptedIncoming(), 0.0001f);
        assertTrue(second.trace().dead());
    }
}
