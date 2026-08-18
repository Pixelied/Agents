package dev.adrien.crystaloptimizer.action;

import dev.adrien.crystaloptimizer.sim.model.CombatState;
import dev.adrien.crystaloptimizer.timing.PacketDependency;

public sealed interface CombatAction permits
    AttackKnownCrystal,
    PlaceCrystal,
    PlaceObsidian,
    PlaceAnchor,
    ChargeAnchor,
    DetonateAnchor,
    SelectHotbarSlot,
    Rotate,
    Wait {

    ActionLegality check(CombatState state);

    ActionOutcome simulate(CombatState state, SimulationServices services);

    PacketDependency dependency();
}
