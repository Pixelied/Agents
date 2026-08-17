package dev.adrien.crystaloptimizer.sim.model;

import net.minecraft.world.entity.EquipmentSlot;

public record SimCombatant(
    float health,
    float absorption,
    EquipmentState equipment,
    EffectState effects,
    BlockingState blocking,
    HurtWindowState hurtWindow,
    TotemState totem,
    boolean dead
) {
    public static SimCombatant testPlayer(float health) {
        return new SimCombatant(
            health,
            0.0f,
            EquipmentState.empty(),
            EffectState.empty(),
            BlockingState.none(),
            new HurtWindowState(0, 0.0f),
            TotemState.NONE,
            health <= 0.0f
        );
    }

    public SimCombatant withChest(ArmorPieceState chest) {
        return withEquipment(equipment.withPiece(EquipmentSlot.CHEST, chest));
    }

    public SimCombatant withEquipment(EquipmentState nextEquipment) {
        return new SimCombatant(health, absorption, nextEquipment, effects, blocking, hurtWindow, totem, dead);
    }

    public SimCombatant withEffects(EffectState nextEffects) {
        return new SimCombatant(health, absorption, equipment, nextEffects, blocking, hurtWindow, totem, dead);
    }

    public SimCombatant withBlocking(BlockingState nextBlocking) {
        return new SimCombatant(health, absorption, equipment, effects, nextBlocking, hurtWindow, totem, dead);
    }

    public SimCombatant withHurtWindow(HurtWindowState nextHurtWindow) {
        return new SimCombatant(health, absorption, equipment, effects, blocking, nextHurtWindow, totem, dead);
    }

    public SimCombatant withTotem(TotemState nextTotem) {
        return new SimCombatant(health, absorption, equipment, effects, blocking, hurtWindow, nextTotem, dead);
    }

    public SimCombatant withDamageState(
        float nextHealth,
        float nextAbsorption,
        EquipmentState nextEquipment,
        EffectState nextEffects,
        HurtWindowState nextHurtWindow,
        TotemState nextTotem,
        boolean nextDead
    ) {
        return new SimCombatant(
            nextHealth,
            nextAbsorption,
            nextEquipment,
            nextEffects,
            blocking,
            nextHurtWindow,
            nextTotem,
            nextDead
        );
    }
}
