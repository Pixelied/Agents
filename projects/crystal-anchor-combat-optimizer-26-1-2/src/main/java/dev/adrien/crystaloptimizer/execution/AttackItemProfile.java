package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/** Source-backed total main-hand attack damage for vanilla 26.1.2 item routing. */
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
        var holder = item.builtInRegistryHolder();
        if (holder.areComponentsBound()) {
            ItemAttributeModifiers modifiers = holder.components().getOrDefault(
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

        // Vanilla's item components are deliberately unbound in bootstrap-free unit tests.
        // Keep the fallback exhaustive for every 26.1.2 vanilla item family whose default
        // main-hand components modify ATTACK_DAMAGE. Live clients always take the bound path.
        return new AttackItemProfile(item, bootstrapFreeVanillaTotal(item));
    }

    private static double bootstrapFreeVanillaTotal(Item item) {
        if (item == Items.WOODEN_SWORD || item == Items.GOLDEN_SWORD) return 4.0;
        if (item == Items.COPPER_SWORD || item == Items.STONE_SWORD) return 5.0;
        if (item == Items.IRON_SWORD) return 6.0;
        if (item == Items.DIAMOND_SWORD) return 7.0;
        if (item == Items.NETHERITE_SWORD) return 8.0;

        if (item == Items.WOODEN_SHOVEL || item == Items.GOLDEN_SHOVEL) return 2.5;
        if (item == Items.COPPER_SHOVEL || item == Items.STONE_SHOVEL) return 3.5;
        if (item == Items.IRON_SHOVEL) return 4.5;
        if (item == Items.DIAMOND_SHOVEL) return 5.5;
        if (item == Items.NETHERITE_SHOVEL) return 6.5;

        if (item == Items.WOODEN_PICKAXE || item == Items.GOLDEN_PICKAXE) return 2.0;
        if (item == Items.COPPER_PICKAXE || item == Items.STONE_PICKAXE) return 3.0;
        if (item == Items.IRON_PICKAXE) return 4.0;
        if (item == Items.DIAMOND_PICKAXE) return 5.0;
        if (item == Items.NETHERITE_PICKAXE) return 6.0;

        if (item == Items.WOODEN_AXE || item == Items.GOLDEN_AXE) return 7.0;
        if (item == Items.COPPER_AXE
            || item == Items.STONE_AXE
            || item == Items.IRON_AXE
            || item == Items.DIAMOND_AXE) return 9.0;
        if (item == Items.NETHERITE_AXE) return 10.0;

        // All 26.1.2 hoes net to the player's vanilla 1.0 base attack damage.
        if (item == Items.WOODEN_HOE
            || item == Items.COPPER_HOE
            || item == Items.STONE_HOE
            || item == Items.GOLDEN_HOE
            || item == Items.IRON_HOE
            || item == Items.DIAMOND_HOE
            || item == Items.NETHERITE_HOE) return 1.0;

        if (item == Items.WOODEN_SPEAR || item == Items.GOLDEN_SPEAR) return 1.0;
        if (item == Items.COPPER_SPEAR || item == Items.STONE_SPEAR) return 2.0;
        if (item == Items.IRON_SPEAR) return 3.0;
        if (item == Items.DIAMOND_SPEAR) return 4.0;
        if (item == Items.NETHERITE_SPEAR) return 5.0;

        if (item == Items.MACE) return 6.0;
        if (item == Items.TRIDENT) return 9.0;
        return PLAYER_BASE_ATTACK_DAMAGE;
    }
}
