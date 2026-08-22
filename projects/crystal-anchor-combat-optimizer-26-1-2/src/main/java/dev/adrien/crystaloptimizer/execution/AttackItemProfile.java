package dev.adrien.crystaloptimizer.execution;

import java.util.Objects;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** Source-backed total main-hand attack damage for vanilla item routing. */
public record AttackItemProfile(Item item, double totalAttackDamage) {
    public AttackItemProfile {
        Objects.requireNonNull(item, "item");
        if (!Double.isFinite(totalAttackDamage) || totalAttackDamage < 0.0) {
            throw new IllegalArgumentException("totalAttackDamage must be finite and non-negative");
        }
    }

    public static AttackItemProfile fromVanillaItem(Item item) {
        Objects.requireNonNull(item, "item");
        return new AttackItemProfile(item, vanillaTotalAttackDamage(item));
    }

    private static double vanillaTotalAttackDamage(Item item) {
        // Player base attack damage is 1.0; the values below are the resulting vanilla totals.
        if (item == Items.WOODEN_SWORD || item == Items.GOLDEN_SWORD) return 4.0;
        if (item == Items.STONE_SWORD) return 5.0;
        if (item == Items.IRON_SWORD) return 6.0;
        if (item == Items.DIAMOND_SWORD) return 7.0;
        if (item == Items.NETHERITE_SWORD) return 8.0;

        if (item == Items.WOODEN_AXE || item == Items.GOLDEN_AXE) return 7.0;
        if (item == Items.STONE_AXE || item == Items.IRON_AXE || item == Items.DIAMOND_AXE) return 9.0;
        if (item == Items.NETHERITE_AXE) return 10.0;

        if (item == Items.WOODEN_PICKAXE || item == Items.GOLDEN_PICKAXE) return 2.0;
        if (item == Items.STONE_PICKAXE) return 3.0;
        if (item == Items.IRON_PICKAXE) return 4.0;
        if (item == Items.DIAMOND_PICKAXE) return 5.0;
        if (item == Items.NETHERITE_PICKAXE) return 6.0;

        if (item == Items.WOODEN_SHOVEL || item == Items.GOLDEN_SHOVEL) return 2.5;
        if (item == Items.STONE_SHOVEL) return 3.5;
        if (item == Items.IRON_SHOVEL) return 4.5;
        if (item == Items.DIAMOND_SHOVEL) return 5.5;
        if (item == Items.NETHERITE_SHOVEL) return 6.5;

        if (item == Items.TRIDENT) return 9.0;
        if (item == Items.MACE) return 6.0;
        return 1.0;
    }
}
