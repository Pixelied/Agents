package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.sim.model.ArmorPieceState;
import dev.adrien.crystaloptimizer.sim.model.EquipmentState;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.world.entity.EquipmentSlot;

public record EquipmentDamage(
    EquipmentState equipment,
    Set<EquipmentSlot> brokenSlots
) {
    public EquipmentDamage {
        brokenSlots = Set.copyOf(brokenSlots);
    }

    public static EquipmentDamage applyExplosionDurability(
        EquipmentState equipment,
        float acceptedDamage,
        DamageRequest request
    ) {
        if (request.bypassesArmor()) {
            return new EquipmentDamage(equipment, Set.of());
        }

        int durabilityDamage = (int) Math.max(1.0f, acceptedDamage / 4.0f);
        EquipmentState next = equipment;
        EnumSet<EquipmentSlot> broken = EnumSet.noneOf(EquipmentSlot.class);

        for (EquipmentSlot slot : new EquipmentSlot[] {
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD
        }) {
            var piece = next.piece(slot);
            if (piece.isEmpty()) {
                continue;
            }

            ArmorPieceState damaged = piece.get().damage(durabilityDamage);
            if (damaged.broken()) {
                next = next.withoutPiece(slot);
                broken.add(slot);
            } else {
                next = next.withPiece(slot, damaged);
            }
        }

        return new EquipmentDamage(next, broken);
    }
}
