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
    void selfUsesPessimisticAbsorptionLowerBoundAndUnknownActiveThreshold() {
        EffectState effects = absorptionTwo();

        SimCombatant self = ObservedCombatantAssembler.self(
            12.0f,
            EquipmentState.empty(),
            effects,
            BlockingState.none(),
            18,
            true,
            false,
            false
        );

        assertEquals(0.0f, self.absorption());
        assertEquals(TotemState.MAINHAND, self.totem());
        assertFalse(self.hurtWindow().lastHurtKnown());
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
