package dev.adrien.crystaloptimizer.sim.damage;

import java.util.Set;
import net.minecraft.world.entity.EquipmentSlot;

public record DamageTrace(
    float rawIncoming,
    float difficultyScaled,
    float blockedDamage,
    float incoming,
    float previousLastHurt,
    float acceptedIncoming,
    float postArmor,
    float postMagic,
    float absorptionConsumed,
    float healthDamage,
    Set<EquipmentSlot> brokenSlots,
    boolean totemTriggered,
    boolean dead
) {
    public DamageTrace {
        brokenSlots = Set.copyOf(brokenSlots);
    }
}
