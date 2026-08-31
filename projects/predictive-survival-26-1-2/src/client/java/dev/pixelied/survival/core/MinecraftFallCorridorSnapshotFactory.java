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
 * Adds a narrow set of blocks along the client's projected fall/glide footprint so long movement
 * is not limited by the fixed nearby-world snapshot cube. Ordinary falling follows the same
 * ballistic assumptions as {@code FallLandingSolver}; fall-flying follows the 26.1.2
 * LivingEntity#updateFallFlyingMovement ordering with the currently observed look/pitch held
 * constant. The capture remains a per-tick swept corridor rather than enlarging the global cube.
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

        // Older/synthetic snapshots may only know collidable/full-cube state. Enrich those partial
        // cells on demand. Live nearby snapshots already carry the vanilla VoxelShape envelope and
        // therefore need no second world lookup.
        List<WorldSnapshot.BlockSnapshot> enrichedNearby = enrichNearbyCollisionShapes(level, nearbyBlocks);
        List<WorldSnapshot.BlockSnapshot> result = new ArrayList<>(enrichedNearby);
        Set<Long> seen = new HashSet<>(Math.max(16, enrichedNearby.size() * 2));
        for (WorldSnapshot.BlockSnapshot block : enrichedNearby) {
            seen.add(BlockPos.containing(
                block.position().x(),
                block.position().y(),
                block.position().z()
            ).asLong());
        }

        if (player.isFallFlying()) {
            captureFallFlyingSweep(level, player, limits, seen, result);
            return List.copyOf(result);
        }

        // The nearby cube already handles grounded/ordinary movement. Only pay for the ordinary
        // fall corridor once the player is actually descending (or has accumulated fall distance).
        if (player.getDeltaMovement().y() >= 0d && player.fallDistance <= 0d) {
            return enrichedNearby;
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

    /**
     * Captures only the cells crossed by each projected fall-flying movement segment. Future input
     * is not knowable, so this is the current-input branch that the bounded predictor consumes; it
     * does not pretend an arbitrary extra radius is authoritative.
     */
    private static void captureFallFlyingSweep(
        ClientLevel level,
        LocalPlayer player,
        EngineLimits limits,
        Set<Long> seen,
        List<WorldSnapshot.BlockSnapshot> output
    ) {
        AABB box = player.getBoundingBox();
        Vec3 velocity = player.getDeltaMovement();
        Vec3 lookAngle = player.getLookAngle();
        float leanAngle = player.getXRot() * (float) (Math.PI / 180.0d);
        double baseGravity = player.getGravity();
        MobEffectInstance slowFalling = player.getEffect(MobEffects.SLOW_FALLING);

        for (long tick = 1; tick <= limits.maxProjectileHorizonTicks(); tick++) {
            double gravity = effectiveGravity(baseGravity, velocity.y(), slowFalling, tick);
            Vec3 nextVelocity = updateFallFlyingMovement(velocity, lookAngle, leanAngle, gravity);
            AABB next = box.move(nextVelocity);
            captureSweptVolume(level, box, next, seen, output);
            box = next;
            velocity = nextVelocity;
        }
    }

    /** Mirrors Minecraft 26.1.2 LivingEntity#updateFallFlyingMovement for one uncollided step. */
    private static Vec3 updateFallFlyingMovement(
        Vec3 movement,
        Vec3 lookAngle,
        float leanAngle,
        double gravity
    ) {
        double lookHorLength = Math.sqrt(lookAngle.x * lookAngle.x + lookAngle.z * lookAngle.z);
        double moveHorLength = movement.horizontalDistance();
        double cos = Math.cos(leanAngle);
        double liftForce = cos * cos;

        movement = movement.add(0.0d, gravity * (-1.0d + liftForce * 0.75d), 0.0d);
        if (movement.y < 0.0d && lookHorLength > 0.0d) {
            double convert = movement.y * -0.1d * liftForce;
            movement = movement.add(
                lookAngle.x * convert / lookHorLength,
                convert,
                lookAngle.z * convert / lookHorLength
            );
        }
        if (leanAngle < 0.0f && lookHorLength > 0.0d) {
            double convert = moveHorLength * -Math.sin(leanAngle) * 0.04d;
            movement = movement.add(
                -lookAngle.x * convert / lookHorLength,
                convert * 3.2d,
                -lookAngle.z * convert / lookHorLength
            );
        }
        if (lookHorLength > 0.0d) {
            movement = movement.add(
                (lookAngle.x / lookHorLength * moveHorLength - movement.x) * 0.1d,
                0.0d,
                (lookAngle.z / lookHorLength * moveHorLength - movement.z) * 0.1d
            );
        }
        return movement.multiply(0.99f, 0.98f, 0.99f);
    }

    private static List<WorldSnapshot.BlockSnapshot> enrichNearbyCollisionShapes(
        ClientLevel level,
        List<WorldSnapshot.BlockSnapshot> nearbyBlocks
    ) {
        List<WorldSnapshot.BlockSnapshot> enriched = null;
        for (int i = 0; i < nearbyBlocks.size(); i++) {
            WorldSnapshot.BlockSnapshot block = nearbyBlocks.get(i);
            if (!block.collision()
                || Boolean.parseBoolean(block.properties().getOrDefault("full_collision_cube", "false"))
                || block.properties().containsKey("collision_min_x")) {
                continue;
            }

            BlockPos pos = BlockPos.containing(
                block.position().x(),
                block.position().y(),
                block.position().z()
            );
            BlockState state = level.getBlockState(pos);
            var shape = state.getCollisionShape(level, pos);
            if (shape.isEmpty()) continue;

            if (enriched == null) enriched = new ArrayList<>(nearbyBlocks);
            Map<String, String> properties = new LinkedHashMap<>(block.properties());
            MinecraftCollisionShapeSnapshot.write(
                properties,
                shape,
                state.isCollisionShapeFullBlock(level, pos)
            );
            enriched.set(i, new WorldSnapshot.BlockSnapshot(
                block.position(),
                block.blockId(),
                true,
                MinecraftCollisionShapeSnapshot.capture(shape, pos),
                properties
            ));
        }
        return enriched == null ? nearbyBlocks : List.copyOf(enriched);
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

        captureCells(level, minX, maxX, minY, maxY, minZ, maxZ, seen, output);
    }

    private static void captureSweptVolume(
        ClientLevel level,
        AABB from,
        AABB to,
        Set<Long> seen,
        List<WorldSnapshot.BlockSnapshot> output
    ) {
        int minX = floor(Math.min(from.minX, to.minX));
        int maxX = floor(Math.max(from.maxX, to.maxX) - GRID_EPSILON);
        int minY = floor(Math.min(from.minY, to.minY));
        int maxY = floor(Math.max(from.maxY, to.maxY) - GRID_EPSILON);
        int minZ = floor(Math.min(from.minZ, to.minZ));
        int maxZ = floor(Math.max(from.maxZ, to.maxZ) - GRID_EPSILON);
        captureCells(level, minX, maxX, minY, maxY, minZ, maxZ, seen, output);
    }

    private static void captureCells(
        ClientLevel level,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        Set<Long> seen,
        List<WorldSnapshot.BlockSnapshot> output
    ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    captureCell(level, new BlockPos(x, y, z), seen, output);
                }
            }
        }
    }

    private static void captureCell(
        ClientLevel level,
        BlockPos pos,
        Set<Long> seen,
        List<WorldSnapshot.BlockSnapshot> output
    ) {
        long key = pos.asLong();
        if (!seen.add(key)) return;

        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        Map<String, String> properties = new LinkedHashMap<>();
        var collisionShape = state.getCollisionShape(level, pos);
        boolean collision = !collisionShape.isEmpty();
        MinecraftCollisionShapeSnapshot.write(
            properties,
            collisionShape,
            state.isCollisionShapeFullBlock(level, pos)
        );
        List<AabbSnapshot> collisionBoxes = MinecraftCollisionShapeSnapshot.capture(collisionShape, pos);

        Vec3 center = pos.getCenter();
        output.add(new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(center.x, center.y, center.z),
            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
            collision,
            collisionBoxes,
            properties
        ));
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
