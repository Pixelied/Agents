package dev.pixelied.survival.core;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.attribute.BedRule;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Extends only triggerable bad-respawn-point sources to their actual vanilla entity-damage reach.
 *
 * <p>The general nearby block snapshot intentionally stays small because it is captured every
 * client tick. Beds and respawn anchors can damage an entity up to {@code 2 * radius = 10} blocks
 * from their power-5 explosion, so an 8-block general cube can otherwise miss a lethal source.
 * This scanner uses section palettes first: sections without either block type require no per-block
 * scan, keeping the common path cheap.</p>
 */
final class MinecraftTriggerableExplosionSnapshotFactory {
    private static final double EXPLOSION_ENTITY_REACH = 10d;
    private static final double EXPLOSION_ENTITY_REACH_SQUARED = EXPLOSION_ENTITY_REACH * EXPLOSION_ENTITY_REACH;
    private static final double CENTER_SCAN_MARGIN = 0.5d;
    private static final double DISTANCE_EPSILON = 1.0E-9d;
    private static final Predicate<BlockState> MAY_CONTAIN_TRIGGERABLE = state ->
        state.getBlock() instanceof BedBlock || state.getBlock() instanceof RespawnAnchorBlock;

    private MinecraftTriggerableExplosionSnapshotFactory() {
    }

    static List<WorldSnapshot.BlockSnapshot> augment(
        ClientLevel level,
        LocalPlayer player,
        List<WorldSnapshot.BlockSnapshot> nearbyBlocks
    ) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(nearbyBlocks, "nearbyBlocks");

        List<WorldSnapshot.BlockSnapshot> result = new ArrayList<>(nearbyBlocks);
        Set<Long> seen = new HashSet<>(Math.max(16, nearbyBlocks.size() * 2));
        for (WorldSnapshot.BlockSnapshot block : nearbyBlocks) {
            seen.add(BlockPos.containing(
                block.position().x(),
                block.position().y(),
                block.position().z()
            ).asLong());
        }

        int minX = floor(player.getX() - EXPLOSION_ENTITY_REACH - CENTER_SCAN_MARGIN);
        int maxX = floor(player.getX() + EXPLOSION_ENTITY_REACH + CENTER_SCAN_MARGIN);
        int minY = Math.max(
            level.getMinY(),
            floor(player.getY() - EXPLOSION_ENTITY_REACH - CENTER_SCAN_MARGIN)
        );
        int maxY = Math.min(
            level.getMaxY(),
            floor(player.getY() + EXPLOSION_ENTITY_REACH + CENTER_SCAN_MARGIN)
        );
        int minZ = floor(player.getZ() - EXPLOSION_ENTITY_REACH - CENTER_SCAN_MARGIN);
        int maxZ = floor(player.getZ() + EXPLOSION_ENTITY_REACH + CENTER_SCAN_MARGIN);

        int minChunkX = SectionPos.blockToSectionCoord(minX);
        int maxChunkX = SectionPos.blockToSectionCoord(maxX);
        int minChunkZ = SectionPos.blockToSectionCoord(minZ);
        int maxChunkZ = SectionPos.blockToSectionCoord(maxZ);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                scanChunk(
                    level,
                    player,
                    chunk,
                    chunkX,
                    chunkZ,
                    minX,
                    maxX,
                    minY,
                    maxY,
                    minZ,
                    maxZ,
                    seen,
                    result
                );
            }
        }

        return List.copyOf(result);
    }

    private static void scanChunk(
        ClientLevel level,
        LocalPlayer player,
        LevelChunk chunk,
        int chunkX,
        int chunkZ,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
        Set<Long> seen,
        List<WorldSnapshot.BlockSnapshot> output
    ) {
        int firstSection = Math.max(0, chunk.getSectionIndex(minY));
        int lastSection = Math.min(chunk.getSections().length - 1, chunk.getSectionIndex(maxY));
        int chunkMinX = chunkX << 4;
        int chunkMinZ = chunkZ << 4;
        int scanMinX = Math.max(minX, chunkMinX);
        int scanMaxX = Math.min(maxX, chunkMinX + 15);
        int scanMinZ = Math.max(minZ, chunkMinZ);
        int scanMaxZ = Math.min(maxZ, chunkMinZ + 15);

        for (int sectionIndex = firstSection; sectionIndex <= lastSection; sectionIndex++) {
            LevelChunkSection section = chunk.getSection(sectionIndex);
            if (!section.maybeHas(MAY_CONTAIN_TRIGGERABLE)) continue;

            int sectionMinY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
            int scanMinY = Math.max(minY, sectionMinY);
            int scanMaxY = Math.min(maxY, sectionMinY + 15);

            for (int x = scanMinX; x <= scanMaxX; x++) {
                int localX = x - chunkMinX;
                for (int z = scanMinZ; z <= scanMaxZ; z++) {
                    int localZ = z - chunkMinZ;
                    for (int y = scanMinY; y <= scanMaxY; y++) {
                        BlockState state = section.getBlockState(localX, y - sectionMinY, localZ);
                        if (!MAY_CONTAIN_TRIGGERABLE.test(state)) continue;

                        BlockPos pos = new BlockPos(x, y, z);
                        if (!withinEntityDamageReach(player, pos)) continue;
                        long key = pos.asLong();
                        if (!seen.add(key)) continue;

                        WorldSnapshot.BlockSnapshot snapshot = snapshotIfTriggerable(level, pos, state);
                        if (snapshot != null) output.add(snapshot);
                    }
                }
            }
        }
    }

    private static boolean withinEntityDamageReach(LocalPlayer player, BlockPos pos) {
        Vec3 center = pos.getCenter();
        return player.distanceToSqr(center) <= EXPLOSION_ENTITY_REACH_SQUARED + DISTANCE_EPSILON;
    }

    private static WorldSnapshot.BlockSnapshot snapshotIfTriggerable(
        ClientLevel level,
        BlockPos pos,
        BlockState state
    ) {
        Map<String, String> properties = new LinkedHashMap<>();

        if (state.getBlock() instanceof BedBlock) {
            BedRule rule = (BedRule) level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos);
            if (!rule.explodes()) return null;

            BedPart part = state.getValue(BedBlock.PART);
            BlockPos headPos = part == BedPart.HEAD ? pos : pos.relative(state.getValue(BedBlock.FACING));
            properties.put("pre_explosion_remove_group", "bed:" + headPos.toShortString());
            if (part == BedPart.HEAD) {
                properties.put("explosion_radius", "5");
                properties.put("triggerable", "true");
                properties.put("source_key", "minecraft:bad_respawn_point");
                properties.put("scales_with_difficulty", "true");
            }
        } else if (state.getBlock() instanceof RespawnAnchorBlock) {
            boolean works = (Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos);
            int charge = state.getValue(RespawnAnchorBlock.CHARGE);
            if (works) return null;

            properties.put("anchor_explodes", "true");
            properties.put("anchor_charge", Integer.toString(charge));
            if (charge > 0) {
                properties.put("explosion_radius", "5");
                properties.put("triggerable", "true");
                properties.put("source_key", "minecraft:bad_respawn_point");
                properties.put("scales_with_difficulty", "true");
                properties.put("pre_explosion_remove_group", "anchor:" + pos.toShortString());
            }
        } else {
            return null;
        }

        var collisionShape = state.getCollisionShape(level, pos);
        boolean collision = !collisionShape.isEmpty();
        MinecraftCollisionShapeSnapshot.write(
            properties,
            collisionShape,
            state.isCollisionShapeFullBlock(level, pos)
        );
        List<AabbSnapshot> collisionBoxes = MinecraftCollisionShapeSnapshot.capture(collisionShape, pos);
        Vec3 center = pos.getCenter();
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(center.x, center.y, center.z),
            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
            collision,
            collisionBoxes,
            properties
        );
    }

    private static int floor(double value) {
        return (int) Math.floor(value);
    }
}
