package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MinecraftBlockingAdapter;
import dev.pixelied.survival.damage.MinecraftEquipmentAdapter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
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

        Map<String, String> state = new LinkedHashMap<>();
        state.put("fall_distance", Double.toString(player.fallDistance));
        state.put("safe_fall_distance", Double.toString(player.getAttributeValue(Attributes.SAFE_FALL_DISTANCE)));
        state.put("fall_damage_multiplier", Double.toString(player.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER)));
        state.put("effective_gravity", Double.toString(effectiveGravity));
        state.put("vertical_friction", "0.98");
        state.put("horizontal_friction", "0.91");
        state.put("world_min_y", Integer.toString(player.level().getMinY()));
        state.put("fall_flying", Boolean.toString(player.isFallFlying()));
        state.put("suppressing_bounce", Boolean.toString(player.isSuppressingBounce()));

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

    private static DifficultySnapshot difficulty(Difficulty difficulty) {
        return switch (difficulty) {
            case PEACEFUL -> DifficultySnapshot.PEACEFUL;
            case EASY -> DifficultySnapshot.EASY;
            case NORMAL -> DifficultySnapshot.NORMAL;
            case HARD -> DifficultySnapshot.HARD;
        };
    }
}
