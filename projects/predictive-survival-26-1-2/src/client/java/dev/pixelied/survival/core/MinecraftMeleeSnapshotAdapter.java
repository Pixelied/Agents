package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.MinecraftDamageAdapter;
import dev.pixelied.survival.threat.VanillaMobMeleeProfile;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.animal.rabbit.Rabbit;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.AttackRange;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reconstructs client-observable vanilla 26.1.2 melee state without trusting unsynced ATTACK_DAMAGE. */
final class MinecraftMeleeSnapshotAdapter {
    private static final double DEFAULT_ATTACK_REACH = Math.sqrt(2.04F) - 0.6F;

    private MinecraftMeleeSnapshotAdapter() {}

    static boolean isPotentialMeleeCandidate(LivingEntity living) {
        if (living instanceof Player player) return !player.isSpectator();
        if (!(living instanceof Mob mob)) return false;
        String typeKey = typeKey(living);
        if ("minecraft:creeper".equals(typeKey)) return false;
        boolean hostileNow = mob instanceof Enemy || mob.isAggressive()
            || living instanceof Rabbit rabbit && rabbit.getVariant() == Rabbit.Variant.EVIL;
        if (!hostileNow) return false;
        AttributeSupplier defaults = defaultAttributes(living);
        return defaults != null && defaults.hasAttribute(Attributes.ATTACK_DAMAGE);
    }

    static Map<String, String> playerProperties(Player player, LocalPlayerView playerView) {
        if (!isPotentialMeleeCandidate(player)) return Map.of();

        ItemStack weapon = player.getMainHandItem();
        ItemStack offhand = player.getOffhandItem();
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("melee_capable", "true");
        properties.put("melee_model", "player");

        // ATTACK_DAMAGE is deliberately not client-syncable in 26.1.2. The server can legally
        // hold a different base value or hidden modifiers than the remote client entity exposes.
        // Keep the lower bound permissive and the upper bound fail-closed instead of treating the
        // client's stale/default AttributeInstance as authoritative.
        properties.put("attack_damage_min", "0");
        properties.put("attack_damage_max", Float.toString(Float.MAX_VALUE));

        // A remote player's attack-strength ticker is likewise not an authoritative server value.
        // Model every legal cooldown state, including a fully charged hit.
        properties.put("attack_strength_min", "0");
        properties.put("attack_strength_max", "1");

        AttributeInstance range = player.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (range != null) properties.put("attack_range", Double.toString(range.getValue()));
        AttributeInstance blockRange = player.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        if (blockRange != null) properties.put("block_interaction_range", Double.toString(blockRange.getValue()));
        properties.put("weapon_key", itemKey(weapon));
        properties.put("offhand_item_key", itemKey(offhand));
        BedRule bedRule = (BedRule) player.level().environmentAttributes().getValue(
            EnvironmentAttributes.BED_RULE,
            player.blockPosition()
        );
        properties.put("bed_explodes", Boolean.toString(bedRule.explodes()));

        // Server fall state can diverge around movement reconciliation. A conservative remote
        // player bound must include a critical/mace-smash-capable state without guessing it.
        properties.put("fall_distance_min", "0");
        properties.put("fall_distance_max", Float.toString(Float.MAX_VALUE));
        properties.put("critical_possible", "unknown");
        properties.put("line_of_sight", Boolean.toString(playerView.hasLineOfSight(player)));
        properties.put("scales_with_difficulty", "false");
        properties.put("source_key", "minecraft:player_attack");
        properties.put("armor_effectiveness_adjustment", Float.toString(MinecraftDamageAdapter.armorEffectivenessAdjustment(weapon)));
        properties.put("fire_aspect_level", Integer.toString(enchantmentLevel(weapon, Enchantments.FIRE_ASPECT)));

        Weapon weaponComponent = weapon.get(DataComponents.WEAPON);
        float disableSeconds = weaponComponent == null ? 0f : Math.max(0f, weaponComponent.disableBlockingForSeconds());
        properties.put("blocking_disable_seconds", Float.toString(disableSeconds));
        properties.put("can_disable_blocking", Boolean.toString(disableSeconds > 0f));
        return Map.copyOf(properties);
    }

    static Map<String, String> mobProperties(Mob mob, LocalPlayerView playerView) {
        if (!isPotentialMeleeCandidate(mob)) return Map.of();
        String typeKey = typeKey(mob);
        AttributeSupplier defaults = defaultAttributes(mob);
        if (defaults == null || !defaults.hasAttribute(Attributes.ATTACK_DAMAGE)) return Map.of();

        AttributeInstance reconstructed = defaults.createInstance(ignored -> {}, Attributes.ATTACK_DAMAGE);
        if (reconstructed == null) return Map.of();

        boolean killerRabbit = mob instanceof Rabbit rabbit && rabbit.getVariant() == Rabbit.Variant.EVIL;
        int syncedSize = mob instanceof Phantom phantom ? phantom.getPhantomSize()
            : mob instanceof Slime slime ? slime.getSize() : 1;
        float defaultBase = (float)defaults.getBaseValue(Attributes.ATTACK_DAMAGE);
        reconstructed.setBaseValue(VanillaMobMeleeProfile.reconstructedAttackAttribute(
            typeKey, defaultBase, mob.isBaby(), syncedSize, false));
        if (killerRabbit) {
            reconstructed.addOrUpdateTransientModifier(new AttributeModifier(
                net.minecraft.resources.Identifier.fromNamespaceAndPath("predictive_survival", "killer_rabbit_attack_bound"),
                5.0,
                AttributeModifier.Operation.ADD_VALUE
            ));
        }

        ItemStack weapon = mob.getWeaponItem();
        weapon.forEachModifier(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute.is(Attributes.ATTACK_DAMAGE)) reconstructed.addOrUpdateTransientModifier(modifier);
        });

        float attackAttribute = (float)Math.max(0d, reconstructed.getValue());
        int sharpness = enchantmentLevel(weapon, Enchantments.SHARPNESS);
        float enchantmentDamage = sharpness <= 0 ? 0f : 1f + 0.5f * (sharpness - 1);
        int density = enchantmentLevel(weapon, Enchantments.DENSITY);
        float genericDamage = VanillaMobMeleeProfile.genericDirectDamage(
            attackAttribute,
            enchantmentDamage,
            itemKey(weapon),
            mob.fallDistance,
            density
        );
        var direct = VanillaMobMeleeProfile.directDamage(typeKey, attackAttribute, mob.isBaby(), genericDamage);
        if (direct.max() <= 0f) return Map.of();

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("melee_capable", "true");
        properties.put("melee_model", "mob");
        if (Float.compare(direct.min(), direct.max()) == 0) {
            properties.put("direct_damage", Float.toString(direct.max()));
        } else {
            properties.put("direct_damage_min", Float.toString(direct.min()));
            properties.put("direct_damage_max", Float.toString(direct.max()));
        }
        properties.put("weapon_key", itemKey(weapon));
        properties.put("fall_distance", Double.toString(mob.fallDistance));
        properties.put("critical_possible", "false");
        properties.put("line_of_sight", Boolean.toString(playerView.hasLineOfSight(mob)));
        properties.put("scales_with_difficulty", "true");
        properties.put("source_key", "minecraft:bee".equals(typeKey) ? "minecraft:sting" : "minecraft:mob_attack");
        properties.put("can_disable_blocking", Boolean.toString(mob.getSecondsToDisableBlocking() > 0f));
        properties.put("blocking_disable_seconds", Float.toString(Math.max(0f, mob.getSecondsToDisableBlocking())));
        properties.put("armor_effectiveness_adjustment", Float.toString(MinecraftDamageAdapter.armorEffectivenessAdjustment(weapon)));
        properties.put("fire_aspect_level", Integer.toString(enchantmentLevel(weapon, Enchantments.FIRE_ASPECT)));
        if ("minecraft:mace".equals(itemKey(weapon)) && mob.fallDistance > 1.5d
            && VanillaMobMeleeProfile.usesGenericItemAttackPipeline(typeKey)) {
            properties.put("mace_smash", "true");
        }
        if ("minecraft:zombie".equals(typeKey) && weapon.isEmpty() && mob.isOnFire()) {
            properties.put("zombie_fire_followup_possible", "true");
        }
        if ("minecraft:wither_skeleton".equals(typeKey)) properties.put("wither_followup_ticks", "200");
        if ("minecraft:cave_spider".equals(typeKey)) {
            properties.put("poison_followup_normal_ticks", "140");
            properties.put("poison_followup_hard_ticks", "300");
        }
        if ("minecraft:bee".equals(typeKey)) {
            properties.put("poison_followup_normal_ticks", "200");
            properties.put("poison_followup_hard_ticks", "360");
        }

        AttackRange range = mob.getActiveItem().get(DataComponents.ATTACK_RANGE);
        if (range == null) {
            properties.put("mob_attack_range_min", "0");
            properties.put("mob_attack_range_max", Double.toString(DEFAULT_ATTACK_REACH));
        } else {
            properties.put("mob_attack_range_min", Float.toString(range.effectiveMinRange(mob)));
            properties.put("mob_attack_range_max", Float.toString(range.effectiveMaxRange(mob)));
        }
        if (mob instanceof Ravager) properties.put("mob_attack_box_deflate", "0.05");
        Entity vehicle = mob.getVehicle();
        if (vehicle != null) putAabb(properties, "vehicle_box_", vehicle.getBoundingBox());
        return Map.copyOf(properties);
    }

    private static AttributeSupplier defaultAttributes(LivingEntity living) {
        if (!DefaultAttributes.hasSupplier(living.getType())) return null;
        @SuppressWarnings("unchecked")
        net.minecraft.world.entity.EntityType<? extends LivingEntity> type =
            (net.minecraft.world.entity.EntityType<? extends LivingEntity>)living.getType();
        return DefaultAttributes.getSupplier(type);
    }

    private static int enchantmentLevel(ItemStack stack, net.minecraft.resources.ResourceKey<net.minecraft.world.item.enchantment.Enchantment> key) {
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        for (var entry : enchantments.entrySet()) if (entry.getKey().is(key)) return entry.getIntValue();
        return 0;
    }

    private static String typeKey(LivingEntity living) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(living.getType()).toString();
    }

    private static String itemKey(ItemStack stack) {
        return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static void putAabb(Map<String, String> properties, String prefix, AABB box) {
        properties.put(prefix + "min_x", Double.toString(box.minX));
        properties.put(prefix + "min_y", Double.toString(box.minY));
        properties.put(prefix + "min_z", Double.toString(box.minZ));
        properties.put(prefix + "max_x", Double.toString(box.maxX));
        properties.put(prefix + "max_y", Double.toString(box.maxY));
        properties.put(prefix + "max_z", Double.toString(box.maxZ));
    }

    interface LocalPlayerView {
        boolean hasLineOfSight(Entity entity);
    }
}
