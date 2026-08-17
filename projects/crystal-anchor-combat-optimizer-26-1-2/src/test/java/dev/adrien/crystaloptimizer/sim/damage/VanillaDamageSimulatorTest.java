package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.ArmorPieceState;
import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import net.minecraft.world.entity.EquipmentSlot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VanillaDamageSimulatorTest {
    @Test
    void armorThatBreaksIsRemovedBeforeSameHitArmorCalculation() {
        var chest = ArmorPieceState.testPiece(8.0f, 3.0f, 1, 0.0f);
        var target = SimCombatant.testPlayer(20.0f).withChest(chest);

        var result = VanillaDamageSimulator.apply(target, DamageRequest.explosion(16.0f));

        assertTrue(result.trace().brokenSlots().contains(EquipmentSlot.CHEST));
        assertEquals(0.0f, result.target().equipment().armorPoints(), 0.0001f);
        assertTrue(result.trace().postArmor() > 0.0f);
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
}
