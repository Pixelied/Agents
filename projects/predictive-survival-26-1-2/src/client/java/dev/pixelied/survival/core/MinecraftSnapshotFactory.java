package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MinecraftBlockingAdapter;
import dev.pixelied.survival.damage.MinecraftEquipmentAdapter;
import dev.pixelied.survival.mixin.FoodDataAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MinecraftSnapshotFactory {
    private final MinecraftEquipmentAdapter equipmentAdapter;
    private final MinecraftBlockingAdapter blockingAdapter;

    public MinecraftSnapshotFactory() {
        this(new MinecraftEquipmentAdapter(), new MinecraftBlockingAdapter());
    }

    MinecraftSnapshotFactory(MinecraftEquipmentAdapter equipmentAdapter, MinecraftBlockingAdapter blockingAdapter) {
        this.equipmentAdapter = equipmentAdapter;
        this.blockingAdapter = blockingAdapter;
    }

    public PlayerSnapshot capture(LocalPlayer player) {
        if (player == null) throw new NullPointerException("player");

        AABB box = player.getBoundingBox();
        Vec3 position = player.position();
        Vec3 velocity = player.getDeltaMovement();
        Vec3 lookAngle = player.getLookAngle();

        Map<String, String> equipmentKeys = new LinkedHashMap<>();
        for (EquipmentSlot slot : EquipmentSlot.VALUES) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                equipmentKeys.put(slot.getName(), stack.getItem().builtInRegistryHolder().getRegisteredName());
            }
        }

        double gravity = player.getGravity();
        double effectiveGravity = velocity.y() <= 0.0 && player.hasEffect(MobEffects.SLOW_FALLING)
            ? Math.min(gravity, 0.01)
            : gravity;

        BlockPos eyeBlock = BlockPos.containing(player.getX(), player.getEyeY(), player.getZ());
        boolean eyeInWater = player.isEyeInFluid(FluidTags.WATER);
        boolean eyeInBubbleColumn = player.level().getBlockState(eyeBlock).is(Blocks.BUBBLE_COLUMN);
        WorldBorder border = player.level().getWorldBorder();
        int crammingOverlapCount = observableCrammingOverlapCount(player, box);

        Map<String, String> state = new LinkedHashMap<>();
        state.put("max_health", Float.toString(player.getMaxHealth()));
        state.put("fall_distance", Double.toString(player.fallDistance));
        state.put("safe_fall_distance", Double.toString(player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)));
        state.put("fall_damage_multiplier", Double.toString(player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)));
        state.put("base_gravity", Double.toString(gravity));
        state.put("effective_gravity", Double.toString(effectiveGravity));
        state.put("vertical_friction", "0.98");
        state.put("horizontal_friction", "0.91");
        state.put("world_min_y", Integer.toString(player.level().getMinY()));
        state.put("fall_flying", Boolean.toString(player.isFallFlying()));
        state.put("elytra_pitch_degrees", Float.toString(player.getXRot()));
        state.put("elytra_look_x", Double.toString(lookAngle.x()));
        state.put("elytra_look_y", Double.toString(lookAngle.y()));
        state.put("elytra_look_z", Double.toString(lookAngle.z()));
        state.put("suppressing_bounce", Boolean.toString(player.isSuppressingBounce()));

        state.put("remaining_fire_ticks", Integer.toString(player.getRemainingFireTicks()));
        state.put("on_fire", Boolean.toString(player.isOnFire()));
        state.put("fire_immune", Boolean.toString(player.fireImmune()));
        state.put("in_lava", Boolean.toString(player.isInLava()));

        state.put("air_supply", Integer.toString(player.getAirSupply()));
        state.put("max_air_supply", Integer.toString(player.getMaxAirSupply()));
        state.put("eye_in_water", Boolean.toString(eyeInWater));
        state.put("eye_in_bubble_column", Boolean.toString(eyeInBubbleColumn));
        state.put("can_breathe_underwater", Boolean.toString(player.canBreatheUnderwater()));
        state.put("oxygen_bonus", Double.toString(player.getAttributeValue(Attributes.OXYGEN_BONUS)));

        state.put("tick_count", Integer.toString(player.tickCount));
        state.put("fully_frozen", Boolean.toString(player.isFullyFrozen()));
        state.put("can_freeze", Boolean.toString(player.canFreeze()));
        state.put("in_wall", Boolean.toString(player.isInWall()));
        state.put("cramming", Boolean.toString(crammingOverlapCount > 0));
        state.put("cramming_observable_overlap_count", Integer.toString(crammingOverlapCount));

        state.put("food_level", Integer.toString(player.getFoodData().getFoodLevel()));
        state.put(
            "food_tick_timer",
            Integer.toString(((FoodDataAccessor) (Object) player.getFoodData()).predictiveSurvival$getTickTimer())
        );

        state.put(
            "border_distance_plus_safe_zone",
            Double.toString(border.getDistanceToBorder(player) + border.getSafeZone())
        );
        state.put("border_damage_per_block", Double.toString(border.getDamagePerBlock()));
        state.put("border_safe_zone", Double.toString(border.getSafeZone()));
        state.put("border_min_x", Double.toString(border.getMinX()));
        state.put("border_max_x", Double.toString(border.getMaxX()));
        state.put("border_min_z", Double.toString(border.getMinZ()));
        state.put("border_max_z", Double.toString(border.getMaxZ()));
        state.put("border_center_x", Double.toString(border.getCenterX()));
        state.put("border_center_z", Double.toString(border.getCenterZ()));
        state.put("border_size", Double.toString(border.getSize()));
        state.put("border_lerp_ticks", Long.toString(Math.max(0L, border.getLerpTime())));
        state.put("border_lerp_target_size", Double.toString(border.getLerpTarget()));
        state.put("border_absolute_max_size", Integer.toString(border.getAbsoluteMaxSize()));

        return new PlayerSnapshot(
            player.getHealth(),
            player.getAbsorptionAmount(),
            player.isInvulnerable(),
            player.getAbilities().invulnerable,
            player.isDeadOrDying(),
            difficulty(player.level().getDifficulty()),
            equipmentAdapter.mitigation(player),
            equipmentAdapter.effects(player),
            blockingAdapter.capture(player),
            HurtState.unknown(),
            equipmentAdapter.deathProtection(player),
            new AabbSnapshot(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ),
            new Vec3Snapshot(position.x(), position.y(), position.z()),
            new Vec3Snapshot(velocity.x(), velocity.y(), velocity.z()),
            equipmentKeys,
            state
        );
    }

    private static int observableCrammingOverlapCount(LocalPlayer player, AABB playerBox) {
        int count = 0;
        ClientLevel level = (ClientLevel) player.level();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity == player || entity.isRemoved() || !entity.isAlive()) continue;
            if (!entity.isPushable() || entity.isPassenger()) continue;
            if (!entity.getBoundingBox().intersects(playerBox)) continue;
            count++;
        }
        return count;
    }

    private static DifficultySnapshot difficulty(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> DifficultySnapshot.PEACEFUL;
            case EASY -> DifficultySnapshot.EASY;
            case NORMAL -> DifficultySnapshot.NORMAL;
            case HARD -> DifficultySnapshot.HARD;
        };
    }
}
