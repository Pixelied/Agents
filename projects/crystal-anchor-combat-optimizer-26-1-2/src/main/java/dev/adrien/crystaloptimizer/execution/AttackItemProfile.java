package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Source-backed total main-hand attack damage for vanilla item routing. */
public record AttackItemProfile(Item item, double totalAttackDamage) {
    private static final double PLAYER_BASE_ATTACK_DAMAGE = 1.0;

    public AttackItemProfile {
        Objects.requireNonNull(item, "item");
        if (!Double.isFinite(totalAttackDamage) || totalAttackDamage < 0.0) {
            throw new IllegalArgumentException("totalAttackDamage must be finite and non-negative");
        }
    }

    public static AttackItemProfile fromVanillaItem(Item item) {
        Objects.requireNonNull(item, "item");
        ItemStack stack = new ItemStack(item);
        ItemAttributeModifiers modifiers = stack.getOrDefault(
            DataComponents.ATTRIBUTE_MODIFIERS,
            ItemAttributeModifiers.EMPTY
        );
        double total = modifiers.compute(
            Attributes.ATTACK_DAMAGE,
            PLAYER_BASE_ATTACK_DAMAGE,
            EquipmentSlot.MAINHAND
        );
        return new AttackItemProfile(item, Math.max(0.0, total));
    }
}
