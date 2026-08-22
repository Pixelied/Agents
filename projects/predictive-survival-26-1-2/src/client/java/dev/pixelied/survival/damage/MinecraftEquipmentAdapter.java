package dev.pixelied.survival.damage;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashSet;
import java.util.Set;

public final class MinecraftEquipmentAdapter {
    public MitigationSnapshot mitigation(LocalPlayer player) {
        if (player == null) throw new NullPointerException("player");

        List<ArmorPieceSnapshot> pieces = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (!slot.isArmor() || slot == EquipmentSlot.BODY) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
            boolean damageOnHurt = stack.isDamageableItem() && equippable != null && equippable.damageOnHurt();
            pieces.add(armorPiece(stack, slot, damageOnHurt));
        }

        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
        boolean helmetPresent = !head.isEmpty();
        int helmetDurability = !helmetPresent
            ? 0
            : head.isDamageableItem()
                ? Math.max(0, head.getMaxDamage() - head.getDamageValue())
                : Integer.MAX_VALUE;

        return new MitigationSnapshot(
            player.getArmorValue(),
            (float) player.getAttributeValue(Attributes.ARMOR_TOUGHNESS),
            helmetPresent,
            helmetDurability,
            pieces
        );
    }

    public ArmorPieceSnapshot armorPiece(ItemStack stack, EquipmentSlot slot, boolean damageOnHurt) {
        if (stack == null) throw new NullPointerException("stack");
        if (slot == null) throw new NullPointerException("slot");
        if (stack.isEmpty()) throw new IllegalArgumentException("stack must not be empty");
        if (!slot.isArmor() || slot == EquipmentSlot.BODY) {
            throw new IllegalArgumentException("Not humanoid armor: " + slot);
        }

        double[] armorAndToughness = {0d, 0d};
        stack.forEachModifier(slot, (attribute, modifier) -> accumulateAddValue(attribute, modifier, armorAndToughness));
        int remainingDurability = stack.isDamageableItem()
            ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue())
            : Integer.MAX_VALUE;

        return new ArmorPieceSnapshot(
            slotFor(slot),
            (float) armorAndToughness[0],
            (float) armorAndToughness[1],
            protectionEnchantments(stack, slot),
            remainingDurability,
            damageOnHurt,
            durabilityResistantDamageTypes(stack)
        );
    }

    public StatusEffectsSnapshot effects(LocalPlayer player) {
        if (player == null) throw new NullPointerException("player");

        Map<String, EffectInstanceSnapshot> effects = new LinkedHashMap<>();
        for (MobEffectInstance instance : player.getActiveEffects()) {
            Holder<MobEffect> effect = instance.getEffect();
            effects.put(
                effect.getRegisteredName(),
                new EffectInstanceSnapshot(effect.getRegisteredName(), instance.getDuration(), instance.getAmplifier())
            );
        }

        MobEffectInstance resistance = player.getEffect(MobEffects.RESISTANCE);
        return new StatusEffectsSnapshot(
            player.hasEffect(MobEffects.FIRE_RESISTANCE),
            resistance == null ? -1 : resistance.getAmplifier(),
            effects
        );
    }

    public DeathProtectionSnapshot deathProtection(LocalPlayer player) {
        if (player == null) throw new NullPointerException("player");
        var main = protectionItem(player.getMainHandItem());
        var off = protectionItem(player.getOffhandItem());
        return new DeathProtectionSnapshot(main, off);
    }

    private static java.util.Optional<DeathProtectionSnapshot.ProtectionItem> protectionItem(ItemStack stack) {
        return MinecraftDeathProtectionAdapter.snapshot(stack);
    }

    private static ProtectionEnchantmentsSnapshot protectionEnchantments(ItemStack stack, EquipmentSlot slot) {
        int protection = 0;
        int blast = 0;
        int projectile = 0;
        int fire = 0;
        int featherFalling = 0;
        int frostWalker = 0;
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) {
            Holder<Enchantment> enchantment = entry.getKey();
            if (!enchantment.value().matchingSlot(slot)) continue;
            int level = entry.getIntValue();
            if (enchantment.is(Enchantments.PROTECTION)) protection = level;
            else if (enchantment.is(Enchantments.BLAST_PROTECTION)) blast = level;
            else if (enchantment.is(Enchantments.PROJECTILE_PROTECTION)) projectile = level;
            else if (enchantment.is(Enchantments.FIRE_PROTECTION)) fire = level;
            else if (enchantment.is(Enchantments.FEATHER_FALLING)) featherFalling = level;
            else if (enchantment.is(Enchantments.FROST_WALKER)) frostWalker = level;
        }
        return new ProtectionEnchantmentsSnapshot(protection, blast, projectile, fire, featherFalling, frostWalker);
    }

    private static Set<String> durabilityResistantDamageTypes(ItemStack stack) {
        DamageResistant resistance = stack.get(DataComponents.DAMAGE_RESISTANT);
        if (resistance == null) return Set.of();
        Set<String> types = new LinkedHashSet<>();
        for (Holder<DamageType> type : resistance.types()) {
            types.add(type.getRegisteredName());
        }
        return Set.copyOf(types);
    }

    private static void accumulateAddValue(
        Holder<Attribute> attribute,
        AttributeModifier modifier,
        double[] armorAndToughness
    ) {
        if (modifier.operation() != AttributeModifier.Operation.ADD_VALUE) return;
        if (attribute.is(Attributes.ARMOR)) armorAndToughness[0] += modifier.amount();
        if (attribute.is(Attributes.ARMOR_TOUGHNESS)) armorAndToughness[1] += modifier.amount();
    }

    private static ArmorPieceSnapshot.Slot slotFor(EquipmentSlot slot) {
        return switch (slot) {
            case FEET -> ArmorPieceSnapshot.Slot.FEET;
            case LEGS -> ArmorPieceSnapshot.Slot.LEGS;
            case CHEST -> ArmorPieceSnapshot.Slot.CHEST;
            case HEAD -> ArmorPieceSnapshot.Slot.HEAD;
            default -> throw new IllegalArgumentException("Not humanoid armor: " + slot);
        };
    }
}
