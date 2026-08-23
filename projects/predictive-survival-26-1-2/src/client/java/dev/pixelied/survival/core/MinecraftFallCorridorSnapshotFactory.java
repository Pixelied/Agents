package dev.pixelied.survival.core;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Adds a narrow set of blocks along the client's projected falling footprint so long falls are not
 * limited by the fixed nearby-world snapshot cube. This deliberately follows the same simple
 * ballistic assumptions as {@code FallLandingSolver}; it does not enlarge the general-purpose
 * block cube used by projectile and explosion prediction.
 */
final class MinecraftFallCorridorSnapshotFactory {
    private static final double HORIZONTAL_FRICTION = 0.91d;
    private static final double VERTICAL_FRICTION = 0.98d;
    private static final double SLOW_FALLING_GRAVITY_CAP = 0.01d;
    private static final double GRID_EPSILON = 1.0E-9d;

    private MinecraftFallCorridorSnapshotFactory() {
    }

    static List<WorldSnapshot.BlockSnapshot> augment(
        ClientLevel level,
        LocalPlayer player,
        EngineLimits limits,
        List<WorldSnapshot.BlockSnapshot> nearbyBlocks
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(nearbyBlocks, "nearbyBlocks");

        // The nearby cube already handles grounded/ordinary movement. Only pay for the corridor
        // once the player is actually descending (or has accumulated fall distance).
        if (player.isFallFlying()
            || player.getDeltaMovement().y() >= 0d && player.fallDistance <= 0d) {
            return nearbyBlocks;
        }

        List<WorldSnapshot.BlockSnapshot> result = new ArrayList<>(nearbyBlocks);
        Set<Long> seen = new HashSet<>(Math.max(16, nearbyBlocks.size() * 2));
        for (WorldSnapshot.BlockSnapshot block : nearbyBlocks) {
            seen.add(BlockPos.containing(
                block.position().x(),
                block.position().y(),
                block.position().z()
            ).asLong());
        }

        AABB box = player.getBoundingBox();
        Vec3 velocity = player.getDeltaMovement();
        double baseGravity = player.getGravity();
        MobEffectInstance slowFalling = player.getEffect(MobEffects.SLOW_FALLING);

        for (long tick = 1; tick <= limits.maxProjectileHorizonTicks(); tick++) {
            AABB next = box.move(velocity);
            if (next.minY < box.minY) {
                captureDescendingSweep(level, box, next, seen, result);
            }

            double gravity = effectiveGravity(baseGravity, velocity.y(), slowFalling, tick);
            velocity = new Vec3(
                velocity.x() * HORIZONTAL_FRICTION,
                (velocity.y() - gravity) * VERTICAL_FRICTION,
                velocity.z() * HORIZONTAL_FRICTION
            );
            box = next;
        }
        return List.copyOf(result);
    }

    private static void captureDescendingSweep(
        ClientLevel level,
        AABB from,
        AABB to,
        Set<Long> seen,
        List<WorldSnapshot.BlockSnapshot> output
    ) {
        int minX = floor(Math.min(from.minX, to.minX));
        int maxX = floor(Math.max(from.maxX, to.maxX) - GRID_EPSILON);
        int minZ = floor(Math.min(from.minZ, to.minZ));
        int maxZ = floor(Math.max(from.maxZ, to.maxZ) - GRID_EPSILON);

        // Landing happens when the player's feet cross a block top. Include one block below the
        // lowest projected feet position plus every Y cell crossed in this movement segment.
        int minY = floor(to.minY) - 1;
        int maxY = floor(from.minY);

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    long key = pos.asLong();
                    if (seen.contains(key)) continue;

                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        // Air does not need a snapshot, but mark it seen so overlapping future
                        // sweeps do not query the same position repeatedly in this capture pass.
                        seen.add(key);
                        continue;
                    }
                    seen.add(key);

                    Map<String, String> properties = new LinkedHashMap<>();
                    var collisionShape = state.getCollisionShape(level, pos);
                    boolean collision = !collisionShape.isEmpty();
                    MinecraftCollisionShapeSnapshot.write(
                        properties,
                        collisionShape,
                        state.isCollisionShapeFullBlock(level, pos)
                    );

                    Vec3 center = pos.getCenter();
                    output.add(new WorldSnapshot.BlockSnapshot(
                        new Vec3Snapshot(center.x, center.y, center.z),
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                        collision,
                        properties
                    ));
                }
            }
        }
    }

    private static double effectiveGravity(
        double baseGravity,
        double verticalVelocity,
        MobEffectInstance slowFalling,
        long futureTick
    ) {
        if (verticalVelocity > 0d || !activeAtFutureMovementTick(slowFalling, futureTick)) {
            return baseGravity;
        }
        return Math.min(baseGravity, SLOW_FALLING_GRAVITY_CAP);
    }

    private static boolean activeAtFutureMovementTick(MobEffectInstance effect, long futureTick) {
        return effect != null
            && (effect.isInfiniteDuration() || (long) effect.getDuration() > futureTick);
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
