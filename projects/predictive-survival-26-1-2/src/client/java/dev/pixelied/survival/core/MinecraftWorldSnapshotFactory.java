package dev.pixelied.survival.core;

import dev.pixelied.survival.mixin.AbstractArrowAccessor;
import dev.pixelied.survival.mixin.FallingBlockEntityAccessor;
import dev.pixelied.survival.mixin.FireworkRocketAccessor;
import dev.pixelied.survival.mixin.PrimedTntAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.entity.projectile.hurtingprojectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownLingeringPotion;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownSplashPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class MinecraftWorldSnapshotFactory {
    private static final int BLOCK_HORIZONTAL_RANGE = 8;
    private static final int BLOCK_VERTICAL_RANGE = 12;

    public WorldSnapshot capture(ClientLevel level, LocalPlayer player, EngineLimits limits) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(limits, "limits");

        List<Entity> tracked = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || !entity.isAlive() || entity.isRemoved()) continue;
            tracked.add(entity);
        }
        tracked.sort(Comparator.comparingDouble(player::distanceToSqr));

        int entityCap = Math.max(limits.maxThreats(), Math.min(Integer.MAX_VALUE / 2, limits.maxThreats() * 4));
        List<WorldSnapshot.EntitySnapshot> entities = new ArrayList<>(Math.min(entityCap, tracked.size()));
        for (int i = 0; i < tracked.size() && entities.size() < entityCap; i++) {
            entities.add(entitySnapshot(tracked.get(i), player));
        }

        return new WorldSnapshot(entities, captureBlocks(level, player.blockPosition()));
    }

    private static WorldSnapshot.EntitySnapshot entitySnapshot(Entity entity, LocalPlayer player) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("no_gravity", Boolean.toString(entity.isNoGravity()));
        properties.put("in_water", Boolean.toString(entity.isInWater()));
        properties.put("in_liquid", Boolean.toString(entity.isInWater()));
        properties.put("horizontal_collision", Boolean.toString(entity.horizontalCollision));
        if (entity instanceof Projectile || entity instanceof AreaEffectCloud) {
            properties.put("observation_age_ticks", "1");
        }
        if (entity instanceof Projectile) {
            properties.put("projectile_age_ticks", Integer.toString(Math.max(0, entity.tickCount)));
        }

        if (entity instanceof AbstractArrow arrow) {
            AbstractArrowAccessor accessor = (AbstractArrowAccessor) (Object) arrow;
            properties.put("base_damage", Double.toString(accessor.predictiveSurvival$getBaseDamage()));
            properties.put("critical", Boolean.toString(arrow.isCritArrow()));
            properties.put("pierce_level", Byte.toString(arrow.getPierceLevel()));
            properties.put("in_ground", Boolean.toString(accessor.predictiveSurvival$isInGround()));
            properties.put("scales_with_difficulty", Boolean.toString(scalesWithDifficulty(arrow.getOwner())));
            if (entity instanceof Arrow potionArrow) {
                properties.put("arrow_tipped", Boolean.toString(potionArrow.getColor() != -1));
            }
            if (entity instanceof ThrownTrident) {
                properties.put("raw_damage", "8");
            }
        }

        if (entity instanceof AbstractHurtingProjectile hurtingProjectile) {
            properties.put("acceleration_power", Double.toString(hurtingProjectile.accelerationPower));
            if (entity instanceof LargeFireball) {
                properties.put("raw_damage", "6");
                properties.put("explosion_radius", "1");
                properties.put("scales_with_difficulty", Boolean.toString(scalesWithDifficulty(hurtingProjectile.getOwner())));
            } else if (entity instanceof SmallFireball) {
                properties.put("raw_damage", "5");
                properties.put("scales_with_difficulty", Boolean.toString(scalesWithDifficulty(hurtingProjectile.getOwner())));
            } else if (entity instanceof WitherSkull) {
                properties.put("raw_damage_min", "5");
                properties.put("raw_damage_max", "8");
                properties.put("explosion_radius", "1");
                properties.put("scales_with_difficulty", Boolean.toString(scalesWithDifficulty(hurtingProjectile.getOwner())));
            }
        }

        if (entity instanceof AreaEffectCloud cloud) {
            properties.put("cloud_waiting", Boolean.toString(cloud.isWaiting()));
        }

        if (entity instanceof ThrownSplashPotion potion) {
            snapshotPotionContents(potion.getItem(), properties);
            properties.put("potion_splash_radius", "4.0");
        } else if (entity instanceof ThrownLingeringPotion potion) {
            snapshotPotionContents(potion.getItem(), properties);
            properties.put("potion_lingering", "true");
        }

        if (entity instanceof FireworkRocketEntity firework) {
            FireworkRocketAccessor accessor = (FireworkRocketAccessor) (Object) firework;
            properties.put("life_ticks", Integer.toString(accessor.predictiveSurvival$getLife()));
            properties.put("lifetime_ticks", Integer.toString(accessor.predictiveSurvival$getLifetime()));
            ItemStack rocket = firework.getItem();
            Fireworks fireworks = rocket.get(DataComponents.FIREWORKS);
            if (fireworks != null) {
                properties.put("firework_explosions", Integer.toString(fireworks.explosions().size()));
            }
        }

        if (entity instanceof ThrownEnderpearl pearl) {
            Entity owner = pearl.getOwner();
            properties.put(
                "owner_is_local_player",
                Boolean.toString(owner != null && owner.getUUID().equals(player.getUUID()))
            );
        }

        if (entity instanceof FallingBlockEntity falling) {
            FallingBlockEntityAccessor accessor = (FallingBlockEntityAccessor) (Object) falling;
            String damageSource = falling.getBlockState().is(BlockTags.ANVIL)
                ? "minecraft:falling_anvil"
                : falling.getBlockState().is(Blocks.POINTED_DRIPSTONE)
                    ? "minecraft:falling_stalactite"
                    : "minecraft:falling_block";
            properties.putAll(new FallingBlockDamageSnapshot(
                accessor.predictiveSurvival$getHurtEntities(),
                accessor.predictiveSurvival$getFallDamageMax(),
                accessor.predictiveSurvival$getFallDamagePerDistance(),
                Math.max(0d, falling.fallDistance),
                damageSource
            ).properties());
        }

        if (entity instanceof PrimedTnt tnt) {
            properties.put("explosion_radius", Float.toString(((PrimedTntAccessor) (Object) tnt).predictiveSurvival$getExplosionPower()));
            properties.put("fuse_ticks", Integer.toString(Math.max(0, tnt.getFuse())));
            properties.put("source_key", "minecraft:explosion");
            properties.put("scales_with_difficulty", "true");
        } else if (entity instanceof EndCrystal) {
            properties.put("explosion_radius", "6");
            properties.put("triggerable", "true");
            properties.put("source_key", "minecraft:explosion");
            properties.put("scales_with_difficulty", "true");
        } else if (entity instanceof Creeper creeper && creeper.getSwellDir() > 0) {
            float progress = Math.max(0f, Math.min(1f, creeper.getSwelling(1f)));
            int conservativeRemaining = Math.max(0, (int) Math.floor((1f - progress) * 28f));
            properties.put("explosion_radius", creeper.isPowered() ? "6" : "3");
            properties.put("fuse_ticks", Integer.toString(conservativeRemaining));
            properties.put("scales_with_difficulty", "true");
        }

        if (entity instanceof LivingEntity living) {
            var attackDamage = living.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attackDamage != null && attackDamage.getValue() > 0d) {
                properties.put("melee_capable", "true");
                properties.put("attack_damage", Double.toString(attackDamage.getValue()));
                var range = living.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
                if (range != null) properties.put("attack_range", Double.toString(range.getValue()));
                properties.put("attack_strength", entity instanceof Player p
                    ? Float.toString(p.getAttackStrengthScale(0f))
                    : "1");
                ItemStack weapon = living.getMainHandItem();
                properties.put("weapon_key", itemKey(weapon));
                properties.put("fall_distance", Double.toString(living.fallDistance));
                properties.put("critical_possible", entity instanceof Player ? "unknown" : "false");
                properties.put("line_of_sight", Boolean.toString(player.hasLineOfSight(entity)));
                properties.put("scales_with_difficulty", Boolean.toString(!(entity instanceof Player)));
                properties.put("source_key", entity instanceof Player ? "minecraft:player_attack" : "minecraft:mob_attack");
                if (itemKey(weapon).endsWith("_axe")) properties.put("can_disable_blocking", "true");
            }
        }

        AABB box = entity.getBoundingBox();
        return new WorldSnapshot.EntitySnapshot(
            Integer.toString(entity.getId()),
            BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
            vec(entity.position()),
            vec(entity.getDeltaMovement()),
            new AabbSnapshot(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
            properties
        );
    }

    private static void snapshotPotionContents(ItemStack stack, Map<String, String> properties) {
        Float durationScale = stack.get(DataComponents.POTION_DURATION_SCALE);
        if (durationScale != null) {
            properties.put("potion_duration_scale", Float.toString(durationScale));
        }

        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return;

        float instantDamage = 0f;
        int poisonDuration = 0;
        int poisonAmplifier = -1;
        int witherDuration = 0;
        int witherAmplifier = -1;
        int statusCount = 0;
        for (MobEffectInstance effect : contents.getAllEffects()) {
            if (effect.getEffect().is(MobEffects.INSTANT_DAMAGE)) {
                int amplifier = Math.max(0, effect.getAmplifier());
                double damage = Math.scalb(6d, amplifier);
                if (!Double.isFinite(damage) || damage >= Float.MAX_VALUE - instantDamage) {
                    instantDamage = Float.MAX_VALUE;
                } else {
                    instantDamage += (float) damage;
                }
            }
            if (effect.getEffect().is(MobEffects.POISON)) {
                int amplifier = Math.max(0, effect.getAmplifier());
                int duration = Math.max(0, effect.getDuration());
                putPotionStatus(properties, statusCount++, "poison", effect.getDuration(), amplifier);
                if (amplifier > poisonAmplifier || amplifier == poisonAmplifier && duration > poisonDuration) {
                    poisonAmplifier = amplifier;
                    poisonDuration = duration;
                }
            }
            if (effect.getEffect().is(MobEffects.WITHER)) {
                int amplifier = Math.max(0, effect.getAmplifier());
                int duration = Math.max(0, effect.getDuration());
                putPotionStatus(properties, statusCount++, "wither", effect.getDuration(), amplifier);
                if (amplifier > witherAmplifier || amplifier == witherAmplifier && duration > witherDuration) {
                    witherAmplifier = amplifier;
                    witherDuration = duration;
                }
            }
        }

        if (statusCount > 0) properties.put("potion_status_count", Integer.toString(statusCount));
        if (instantDamage > 0f) {
            properties.put("potion_instant_damage", Float.toString(instantDamage));
            properties.put("potion_source_key", "minecraft:indirect_magic");
        }
        if (poisonAmplifier >= 0 && poisonDuration > 0) {
            properties.put("potion_poison_duration_ticks", Integer.toString(poisonDuration));
            properties.put("potion_poison_amplifier", Integer.toString(poisonAmplifier));
        }
        if (witherAmplifier >= 0 && witherDuration > 0) {
            properties.put("potion_wither_duration_ticks", Integer.toString(witherDuration));
            properties.put("potion_wither_amplifier", Integer.toString(witherAmplifier));
        }
    }

    private static void putPotionStatus(
        Map<String, String> properties,
        int index,
        String kind,
        int duration,
        int amplifier
    ) {
        String prefix = "potion_status_" + index + "_";
        properties.put(prefix + "kind", kind);
        properties.put(prefix + "duration_ticks", Integer.toString(duration));
        properties.put(prefix + "amplifier", Integer.toString(amplifier));
    }

    private static List<WorldSnapshot.BlockSnapshot> captureBlocks(ClientLevel level, BlockPos center) {
        List<WorldSnapshot.BlockSnapshot> blocks = new ArrayList<>();
        for (int dx = -BLOCK_HORIZONTAL_RANGE; dx <= BLOCK_HORIZONTAL_RANGE; dx++) {
            for (int dz = -BLOCK_HORIZONTAL_RANGE; dz <= BLOCK_HORIZONTAL_RANGE; dz++) {
                for (int dy = -BLOCK_VERTICAL_RANGE; dy <= BLOCK_VERTICAL_RANGE; dy++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) continue;

                    Map<String, String> properties = new LinkedHashMap<>();
                    boolean collision = !state.getCollisionShape(level, pos).isEmpty();
                    properties.put("full_collision_cube", Boolean.toString(state.isCollisionShapeFullBlock(level, pos)));

                    if (state.getBlock() instanceof BedBlock) {
                        BedRule rule = (BedRule) level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos);
                        if (rule.explodes()) {
                            properties.put("explosion_radius", "5");
                            properties.put("triggerable", "true");
                            properties.put("source_key", "minecraft:bad_respawn_point");
                            properties.put("scales_with_difficulty", "true");
                        }
                    } else if (state.getBlock() instanceof RespawnAnchorBlock) {
                        boolean works = (Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos);
                        if (!works && state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
                            properties.put("explosion_radius", "5");
                            properties.put("triggerable", "true");
                            properties.put("source_key", "minecraft:bad_respawn_point");
                            properties.put("scales_with_difficulty", "true");
                        }
                    }

                    var blockCenter = pos.getCenter();
                    blocks.add(new WorldSnapshot.BlockSnapshot(
                        new Vec3Snapshot(blockCenter.x, blockCenter.y, blockCenter.z),
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                        collision,
                        properties
                    ));
                }
            }
        }
        return List.copyOf(blocks);
    }

    private static boolean scalesWithDifficulty(Entity owner) {
        return owner instanceof LivingEntity && !(owner instanceof Player);
    }

    private static String itemKey(ItemStack stack) {
        return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static Vec3Snapshot vec(net.minecraft.world.phys.Vec3 value) {
        return new Vec3Snapshot(value.x, value.y, value.z);
    }
}
