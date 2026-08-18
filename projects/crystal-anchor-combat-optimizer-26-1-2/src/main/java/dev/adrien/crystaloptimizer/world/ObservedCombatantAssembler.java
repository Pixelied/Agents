package dev.adrien.crystaloptimizer.world;

import dev.adrien.crystaloptimizer.intel.RemoteCombatObservationPolicy;
import dev.adrien.crystaloptimizer.sim.model.BlockingState;
import dev.adrien.crystaloptimizer.sim.model.EffectState;
import dev.adrien.crystaloptimizer.sim.model.EquipmentState;
import dev.adrien.crystaloptimizer.sim.model.SimCombatant;
import dev.adrien.crystaloptimizer.sim.model.TotemState;
import java.util.Objects;

public final class ObservedCombatantAssembler {
    public static SimCombatant self(
        float health,
        EquipmentState equipment,
        EffectState effects,
        BlockingState blocking,
        int invulnerableTime,
        boolean mainHandTotem,
        boolean offHandTotem,
        boolean dead
    ) {
        return assemble(
            health,
            0.0f,
            equipment,
            effects,
            blocking,
            invulnerableTime,
            mainHandTotem,
            offHandTotem,
            dead
        );
    }

    public static SimCombatant target(
        float health,
        EquipmentState equipment,
        EffectState effects,
        BlockingState blocking,
        int invulnerableTime,
        boolean mainHandTotem,
        boolean offHandTotem,
        boolean dead
    ) {
        Objects.requireNonNull(effects, "effects");
        return assemble(
            health,
            RemoteCombatObservationPolicy.absorptionUpperBound(effects),
            equipment,
            effects,
            blocking,
            invulnerableTime,
            mainHandTotem,
            offHandTotem,
            dead
        );
    }

    private static SimCombatant assemble(
        float health,
        float absorption,
        EquipmentState equipment,
        EffectState effects,
        BlockingState blocking,
        int invulnerableTime,
        boolean mainHandTotem,
        boolean offHandTotem,
        boolean dead
    ) {
        Objects.requireNonNull(equipment, "equipment");
        Objects.requireNonNull(effects, "effects");
        Objects.requireNonNull(blocking, "blocking");
        TotemState totem = RemoteCombatObservationPolicy.visibleTotem(mainHandTotem, offHandTotem);
        return new SimCombatant(
            Math.max(0.0f, health),
            Math.max(0.0f, absorption),
            equipment,
            effects,
            blocking,
            RemoteCombatObservationPolicy.hurtWindow(invulnerableTime),
            totem,
            dead
        );
    }

    private ObservedCombatantAssembler() {
    }
}
