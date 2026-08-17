package dev.pixelied.survival.core;

import dev.pixelied.survival.damage.HurtState;
import dev.pixelied.survival.damage.MinecraftBlockingAdapter;
import dev.pixelied.survival.damage.MinecraftEquipmentAdapter;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EquipmentSlot;
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
            equipmentKeys
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
