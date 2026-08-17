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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DeathProtection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MinecraftEquipmentAdapter {
    public MitigationSnapshot mitigation(LocalPlayer player) {
        if (player == null) throw new NullPointerException("player");

        List<ArmorPieceSnapshot> pieces = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            if (!slot.isArmor() || slot == EquipmentSlot.BODY) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (stack.isEmpty()) continue;

            double[] armorAndToughness = {0d, 0d};
            stack.forEachModifier(slot, (attribute, modifier) -> accumulateAddValue(attribute, modifier, armorAndToughness));

            int remainingDurability = stack.isDamageableItem()
                ? Math.max(0, stack.getMaxDamage() - stack.getDamageValue())
                : Integer.MAX_VALUE;
            pieces.add(new ArmorPieceSnapshot(
                slotFor(slot),
                (float) armorAndToughness[0],
                (float) armorAndToughness[1],
                0,
                remainingDurability,
                stack.isDamageableItem()
            ));
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
            1f,
            0,
            helmetPresent,
            helmetDurability,
            pieces
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
        DeathProtection component = stack.get(DataComponents.DEATH_PROTECTION);
        if (component == null) return java.util.Optional.empty();
        DeathProtectionSnapshot.ProtectionItem item = component.equals(DeathProtection.TOTEM_OF_UNDYING)
            ? DeathProtectionSnapshot.ProtectionItem.vanillaTotem()
            : DeathProtectionSnapshot.ProtectionItem.generic();
        return java.util.Optional.of(item);
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
