package dev.pixelied.survival.core;

import dev.pixelied.survival.mixin.AbstractArrowAccessor;
import dev.pixelied.survival.mixin.FallingBlockEntityAccessor;
import dev.pixelied.survival.mixin.FireworkRocketAccessor;
import dev.pixelied.survival.timing.RemoteEntityKinematicEnvelope;
import dev.pixelied.survival.timing.TimingSnapshot;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.EvokerFangs;
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
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class MinecraftWorldSnapshotFactory {
    private static final String OBSERVATION_OVERFLOW_TYPE = "predictive_survival:observation_overflow";
    private static final int REMOTE_KINEMATIC_HISTORY_LIMIT = 8;

    private final Set<Integer> pendingDiscontinuities = new LinkedHashSet<>();
    private RemoteEntityKinematicEnvelope remoteKinematics;
    private int remoteKinematicEntityLimit = -1;
    private LocalPlayer remoteKinematicPlayer;

    public WorldSnapshot capture(ClientLevel level, LocalPlayer player, EngineLimits limits) {
        return captureInternal(level, player, limits, null);
    }

    public WorldSnapshot capture(
        ClientLevel level,
        LocalPlayer player,
        EngineLimits limits,
        TimingSnapshot timing
    ) {
        Objects.requireNonNull(timing, "timing");
        return captureInternal(level, player, limits, timing);
    }

    public void markEntityDiscontinuity(int entityId) {
        if (remoteKinematics == null) return;
        pendingDiscontinuities.remove(entityId);
        pendingDiscontinuities.add(entityId);
        while (pendingDiscontinuities.size() > remoteKinematicEntityLimit) {
            pendingDiscontinuities.remove(pendingDiscontinuities.iterator().next());
        }
    }

    public void reset() {
        if (remoteKinematics != null) remoteKinematics.reset();
        pendingDiscontinuities.clear();
        remoteKinematics = null;
        remoteKinematicEntityLimit = -1;
        remoteKinematicPlayer = null;
    }

    private WorldSnapshot captureInternal(
        ClientLevel level,
        LocalPlayer player,
        EngineLimits limits,
        TimingSnapshot timing
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(limits, "limits");

        int entityCap = Math.max(limits.maxThreats(), Math.min(Integer.MAX_VALUE / 2, limits.maxThreats() * 4));
        RemoteEntityKinematicEnvelope kinematics = timing == null ? null : kinematicsFor(entityCap, player);
        Comparator<Entity> trackedOrder = Comparator
            .comparingInt(MinecraftWorldSnapshotFactory::threatPriority)
            .thenComparingDouble(player::distanceToSqr);
        BoundedTopKAccumulator<Entity> tracked = new BoundedTopKAccumulator<>(
            entityCap,
            trackedOrder,
            entity -> threatPriority(entity) == 0
        );
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || !entity.isAlive() || entity.isRemoved()) continue;
            tracked.offer(entity);
        }

        BoundedTopKAccumulator.Selection<Entity> selection = tracked.finish();
        List<Entity> selectedEntities = selection.selected();
        boolean overflowed = selection.omittedRelevant() > 0;
        int realEntityLimit = overflowed ? Math.max(0, entityCap - 1) : entityCap;

        List<WorldSnapshot.EntitySnapshot> entities = new ArrayList<>(
            Math.min(entityCap, selectedEntities.size() + 1)
        );
        for (int i = 0; i < selectedEntities.size() && entities.size() < realEntityLimit; i++) {
            Entity entity = selectedEntities.get(i);
            boolean discontinuity = kinematics != null && pendingDiscontinuities.remove(entity.getId());
            entities.add(entitySnapshot(entity, player, kinematics, timing, discontinuity));
        }
        if (overflowed) {
            long omittedRelevant = selection.omittedRelevant() + 1L;
            int omittedRelevantEntities = (int) Math.min(Integer.MAX_VALUE, omittedRelevant);
            entities.add(observationOverflowMarker(player, omittedRelevantEntities));
        }

        List<WorldSnapshot.BlockSnapshot> nearbyBlocks = MinecraftNearbyBlockSnapshotFactory.capture(
            level,
            player.blockPosition()
        );
        List<WorldSnapshot.BlockSnapshot> fallAwareBlocks = MinecraftFallCorridorSnapshotFactory.augment(
            level,
            player,
            limits,
            nearbyBlocks
        );
        List<WorldSnapshot.BlockSnapshot> blocks = MinecraftTriggerableExplosionSnapshotFactory.augment(
            level,
            player,
            fallAwareBlocks
        );
        return new WorldSnapshot(entities, blocks);
    }

    private RemoteEntityKinematicEnvelope kinematicsFor(int entityCap, LocalPlayer player) {
        if (remoteKinematics == null
            || remoteKinematicEntityLimit != entityCap
            || remoteKinematicPlayer != player) {
            remoteKinematics = new RemoteEntityKinematicEnvelope(REMOTE_KINEMATIC_HISTORY_LIMIT, entityCap);
            pendingDiscontinuities.clear();
            remoteKinematicEntityLimit = entityCap;
            remoteKinematicPlayer = player;
        }
        return remoteKinematics;
    }

    private static int threatPriority(Entity entity) {
        return isThreatRelevant(entity) ? 0 : 1;
    }

    private static boolean isThreatRelevant(Entity entity) {
        if (entity instanceof AreaEffectCloud
            || entity instanceof EvokerFangs
            || entity instanceof LightningBolt
            || entity instanceof FallingBlockEntity
            || entity instanceof PrimedTnt
            || entity instanceof EndCrystal
            || entity instanceof MinecartTNT) {
            return true;
        }
        if (entity instanceof Creeper creeper
            && MinecraftExplosionSnapshotRules.creeperRelevant(creeper.isIgnited(), creeper.getSwellDir())) return true;
        if (entity instanceof WitherBoss wither && wither.getInvulnerableTicks() > 0) return true;
        if (entity instanceof LivingEntity living && MinecraftMeleeSnapshotAdapter.isPotentialMeleeCandidate(living)) {
            return true;
        }
        if (!(entity instanceof Projectile)) return false;

        String typeKey = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return switch (typeKey) {
            case "minecraft:arrow", "minecraft:spectral_arrow", "minecraft:trident",
                 "minecraft:spear", "minecraft:thrown_spear", "minecraft:llama_spit",
                 "minecraft:fireball", "minecraft:small_fireball", "minecraft:dragon_fireball",
                 "minecraft:wither_skull", "minecraft:wind_charge", "minecraft:breeze_wind_charge",
                 "minecraft:firework_rocket", "minecraft:potion", "minecraft:splash_potion",
                 "minecraft:lingering_potion", "minecraft:ender_pearl", "minecraft:shulker_bullet" -> true;
            default -> false;
        };
    }

    private static WorldSnapshot.EntitySnapshot observationOverflowMarker(LocalPlayer player, int omittedRelevantEntities) {
        AABB box = player.getBoundingBox();
        return new WorldSnapshot.EntitySnapshot(
            OBSERVATION_OVERFLOW_TYPE,
            OBSERVATION_OVERFLOW_TYPE,
            vec(player.position()),
            new Vec3Snapshot(0d, 0d, 0d),
            new AabbSnapshot(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
            Map.of("omitted_relevant_entities", Integer.toString(Math.max(1, omittedRelevantEntities)))
        );
    }

    private static WorldSnapshot.EntitySnapshot entitySnapshot(
        Entity entity,
        LocalPlayer player,
        RemoteEntityKinematicEnvelope kinematics,
        TimingSnapshot timing,
        boolean discontinuity
    ) {
        Map<String, String> properties = new LinkedHashMap<>();
        properties.put("no_gravity", Boolean.toString(entity.isNoGravity()));
        properties.put("in_water", Boolean.toString(entity.isInWater()));
        properties.put("in_liquid", Boolean.toString(entity.isInWater()));
        properties.put("horizontal_collision", Boolean.toString(entity.horizontalCollision));
        if (entity instanceof Projectile || entity instanceof AreaEffectCloud) {
            properties.put("observation_age_ticks", "1");
        }
        if (entity instanceof Projectile projectile) {
            properties.put("projectile", "true");
            properties.put("on_fire", Boolean.toString(projectile.isOnFire()));
            properties.put("projectile_age_ticks", Integer.toString(Math.max(0, entity.tickCount)));
            properties.put(
                "scales_with_difficulty",
                Boolean.toString(conservativeScalesWithDifficulty(projectile.getOwner(), player.level().getDifficulty()))
            );
        }

        if (entity instanceof AbstractArrow arrow) {
            properties.put("abstract_arrow", "true");
            properties.put("on_fire", Boolean.toString(arrow.isOnFire()));
            AbstractArrowAccessor accessor = (AbstractArrowAccessor) (Object) arrow;
            properties.put("base_damage", Double.toString(accessor.predictiveSurvival$getBaseDamage()));
            properties.put("critical", Boolean.toString(arrow.isCritArrow()));
            properties.put("pierce_level", Byte.toString(arrow.getPierceLevel()));
            properties.put("in_ground", Boolean.toString(accessor.predictiveSurvival$isInGround()));
            if (entity instanceof Arrow potionArrow) {
                properties.put("arrow_tipped", Boolean.toString(potionArrow.getColor() != -1));
            }
            if (entity instanceof ThrownTrident) {
                properties.put("raw_damage", "8");
            }
        }

        if (entity instanceof AbstractHurtingProjectile hurtingProjectile) {
            Entity owner = hurtingProjectile.getOwner();
            boolean scalesWithDifficulty = conservativeScalesWithDifficulty(owner, player.level().getDifficulty());
            properties.put("acceleration_power", Double.toString(hurtingProjectile.accelerationPower));
            if (entity instanceof LargeFireball) {
                properties.put("raw_damage", "6");
                properties.put("explosion_radius", "1");
                properties.put("source_key", owner == null ? "minecraft:unattributed_fireball" : "minecraft:fireball");
                properties.put("scales_with_difficulty", Boolean.toString(scalesWithDifficulty));
            } else if (entity instanceof SmallFireball) {
                properties.put("raw_damage", "5");
                properties.put("source_key", owner == null ? "minecraft:unattributed_fireball" : "minecraft:fireball");
                properties.put("scales_with_difficulty", Boolean.toString(scalesWithDifficulty));
            } else if (entity instanceof WitherSkull) {
                properties.put("explosion_radius", "1");
                if (owner instanceof LivingEntity) {
                    properties.put("raw_damage", "8");
                    properties.put("source_key", "minecraft:wither_skull");
                    properties.put("scales_with_difficulty", Boolean.toString(scalesWithDifficulty));
                } else {
                    properties.put("raw_damage_min", "5");
                    properties.put("raw_damage_max", "8");
                    properties.put("source_key", "minecraft:magic");
                    properties.put("scales_with_difficulty", Boolean.toString(scalesWithDifficulty));
                }
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
            properties.putAll(MinecraftExplosionSnapshotRules.primedTnt(tnt.getFuse()));
            putExplosionCenter(properties, tnt.getX(), tnt.getY(0.0625), tnt.getZ());
        } else if (entity instanceof MinecartTNT minecart) {
            properties.putAll(MinecraftExplosionSnapshotRules.tntMinecart(minecart.isPrimed(), minecart.getFuse()));
            properties.put("fall_distance", Double.toString(Math.max(0d, minecart.fallDistance)));
            properties.put("tnt_explodes", "unknown");
        } else if (entity instanceof EndCrystal) {
            properties.put("explosion_radius", "6");
            properties.put("triggerable", "true");
            properties.put("source_key", "minecraft:explosion");
            properties.put("scales_with_difficulty", "true");
        } else if (entity instanceof Creeper creeper
            && MinecraftExplosionSnapshotRules.creeperRelevant(creeper.isIgnited(), creeper.getSwellDir())) {
            properties.putAll(MinecraftExplosionSnapshotRules.creeper(
                creeper.isPowered(),
                creeper.isIgnited(),
                creeper.getSwellDir(),
                creeper.getSwelling(1f)
            ));
        } else if (entity instanceof WitherBoss wither && wither.getInvulnerableTicks() > 0) {
            properties.putAll(MinecraftExplosionSnapshotRules.witherSpawn(wither.getInvulnerableTicks()));
            putExplosionCenter(properties, wither.getX(), wither.getEyeY(), wither.getZ());
        }

        if (entity instanceof Mob mob) {
            properties.putAll(MinecraftMeleeSnapshotAdapter.mobProperties(mob, player::hasLineOfSight));
        } else if (entity instanceof Player remotePlayer) {
            properties.putAll(MinecraftMeleeSnapshotAdapter.playerProperties(remotePlayer, player::hasLineOfSight));
        }

        Vec3 renderedPosition = entity.position();
        Vec3 snapshotPosition = renderedPosition;
        if (entity instanceof MinecartTNT) {
            var interpolation = entity.getInterpolation();
            if (interpolation != null && interpolation.hasActiveInterpolation()) {
                snapshotPosition = interpolation.position();
            }
        }
        AABB box = entity.getBoundingBox();
        if (!snapshotPosition.equals(renderedPosition)) {
            box = box.move(snapshotPosition.subtract(renderedPosition));
        }

        Vec3Snapshot position = vec(snapshotPosition);
        Vec3Snapshot velocity = vec(entity.getDeltaMovement());
        if (kinematics != null && timing != null && isThreatRelevant(entity)) {
            RemoteEntityKinematicEnvelope.Snapshot observation = kinematics.observe(
                Integer.toString(entity.getId()),
                entity.getUUID().toString(),
                timing.clientTick(),
                position,
                velocity,
                timing,
                discontinuity
            );
            TickWindow age = observation.observationAgeTicks();
            properties.put("observation_age_min_ticks", Long.toString(age.earliest()));
            properties.put("observation_age_max_ticks", Long.toString(age.latest()));
            properties.put("kinematic_history_samples", Integer.toString(observation.history().size()));
            properties.put("kinematic_reset_boundary", Boolean.toString(observation.resetBoundary()));
        }

        return new WorldSnapshot.EntitySnapshot(
            Integer.toString(entity.getId()),
            BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
            position,
            velocity,
            new AabbSnapshot(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
            properties
        );
    }

    private static void putExplosionCenter(Map<String, String> properties, double x, double y, double z) {
        properties.put("explosion_center_x", Double.toString(x));
        properties.put("explosion_center_y", Double.toString(y));
        properties.put("explosion_center_z", Double.toString(z));
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

    private static boolean conservativeScalesWithDifficulty(Entity owner, Difficulty difficulty) {
        if (owner != null) return scalesWithDifficulty(owner);
        return difficulty == Difficulty.HARD;
    }

    private static boolean scalesWithDifficulty(Entity owner) {
        return owner instanceof LivingEntity && !(owner instanceof Player);
    }

    private static String itemKey(ItemStack stack) {
        return stack.isEmpty() ? "minecraft:air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    private static Vec3Snapshot vec(net.minecraft.world.phys.Vec3 value) {
        return new Vec3Snapshot(value.x(), value.y(), value.z());
    }
}
