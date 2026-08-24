package dev.adrien.crystaloptimizer.world;

import dev.adrien.crystaloptimizer.sim.model.BlockingState;
import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.EquipmentState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ObservedCombatantAssemblerTest {
    @Test
    void selfPreservesObservedAbsorption() {
        SimCombatant self = ObservedCombatantAssembler.self(
            9.0f,
            4.0f,
            EquipmentState.empty(),
            EffectState.empty(),
            BlockingState.none(),
            0,
            false,
            true,
            false
        );

        assertEquals(4.0f, self.absorption());
        assertEquals(13.0f, self.health() + self.absorption());
        assertEquals(TotemState.OFFHAND, self.totem());
    }

    @Test
    void targetUsesAbsorptionUpperBoundAndOnlyVisibleHandTotems() {
        SimCombatant target = ObservedCombatantAssembler.target(
            12.0f,
            EquipmentState.empty(),
            absorptionTwo(),
            BlockingState.none(),
            18,
            false,
            true,
            false
        );

        assertEquals(8.0f, target.absorption());
        assertEquals(TotemState.OFFHAND, target.totem());
        assertFalse(target.hurtWindow().lastHurtKnown());
    }

    @Test
    void deadFlagIsPreservedWithoutHiddenResourceInference() {
        SimCombatant target = ObservedCombatantAssembler.target(
            0.0f,
            EquipmentState.empty(),
            EffectState.empty(),
            BlockingState.none(),
            0,
            false,
            false,
            true
        );

        assertEquals(TotemState.NONE, target.totem());
        assertEquals(0.0f, target.absorption());
        assertEquals(0.0f, target.health());
        assertEquals(true, target.dead());
    }

    private static EffectState absorptionTwo() {
        return new EffectState(
            Optional.empty(),
            Optional.empty(),
            Optional.of(new EffectState.EffectInstance(1, 80)),
            Optional.empty()
        );
    }
}
