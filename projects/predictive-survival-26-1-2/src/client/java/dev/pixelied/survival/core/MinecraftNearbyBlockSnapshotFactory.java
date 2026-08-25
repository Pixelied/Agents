package dev.pixelied.survival.core;

import net.minecraft.client.multiplayer.ClientLevel;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

/** Captures the fixed nearby block cube without thousands of ClientLevel lookups per frame. */
final class MinecraftNearbyBlockSnapshotFactory {
    private static final int HORIZONTAL_RANGE = 8;
    private static final int VERTICAL_RANGE = 12;
    private static final Predicate<BlockState> NON_AIR = state -> !state.isAir();
    private static final Comparator<WorldSnapshot.BlockSnapshot> VANILLA_SCAN_ORDER = Comparator
        .comparingDouble((WorldSnapshot.BlockSnapshot block) -> block.position().x())
        .thenComparingDouble(block -> block.position().z())
        .thenComparingDouble(block -> block.position().y());

    private MinecraftNearbyBlockSnapshotFactory() {
    }

    static List<WorldSnapshot.BlockSnapshot> capture(ClientLevel level, BlockPos center) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(center, "center");

        int minX = center.getX() - HORIZONTAL_RANGE;
        int maxX = center.getX() + HORIZONTAL_RANGE;
        int minY = Math.max(level.getMinY(), center.getY() - VERTICAL_RANGE);
        int maxY = Math.min(level.getMaxY(), center.getY() + VERTICAL_RANGE);
        int minZ = center.getZ() - HORIZONTAL_RANGE;
        int maxZ = center.getZ() + HORIZONTAL_RANGE;

        List<WorldSnapshot.BlockSnapshot> blocks = new ArrayList<>();
        int minChunkX = SectionPos.blockToSectionCoord(minX);
        int maxChunkX = SectionPos.blockToSectionCoord(maxX);
        int minChunkZ = SectionPos.blockToSectionCoord(minZ);
        int maxChunkZ = SectionPos.blockToSectionCoord(maxZ);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
                if (chunk == null) continue;
                scanChunk(level, chunk, chunkX, chunkZ, minX, maxX, minY, maxY, minZ, maxZ, blocks);
            }
        }
        blocks.sort(VANILLA_SCAN_ORDER);
        return List.copyOf(blocks);
    }

    private static void scanChunk(
        ClientLevel level,
        LevelChunk chunk,
        int chunkX,
        int chunkZ,
        int minX,
        int maxX,
        int minY,
        int maxY,
        int minZ,
        int maxZ,
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
            if (!section.maybeHas(NON_AIR)) continue;

            int sectionMinY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
            int scanMinY = Math.max(minY, sectionMinY);
            int scanMaxY = Math.min(maxY, sectionMinY + 15);
            for (int x = scanMinX; x <= scanMaxX; x++) {
                int localX = x - chunkMinX;
                for (int z = scanMinZ; z <= scanMaxZ; z++) {
                    int localZ = z - chunkMinZ;
                    for (int y = scanMinY; y <= scanMaxY; y++) {
                        BlockState state = section.getBlockState(localX, y - sectionMinY, localZ);
                        if (state.isAir()) continue;
                        output.add(snapshot(level, new BlockPos(x, y, z), state));
                    }
                }
            }
        }
    }

    private static WorldSnapshot.BlockSnapshot snapshot(ClientLevel level, BlockPos pos, BlockState state) {
        Map<String, String> properties = new LinkedHashMap<>();
        var collisionShape = state.getCollisionShape(level, pos);
        boolean collision = !collisionShape.isEmpty();
        MinecraftCollisionShapeSnapshot.write(
            properties,
            collisionShape,
            state.isCollisionShapeFullBlock(level, pos)
        );
        List<AabbSnapshot> collisionBoxes = MinecraftCollisionShapeSnapshot.capture(collisionShape, pos);

        if (state.getBlock() instanceof BedBlock) {
            BedRule rule = (BedRule) level.environmentAttributes().getValue(EnvironmentAttributes.BED_RULE, pos);
            if (rule.explodes()) {
                BedPart part = state.getValue(BedBlock.PART);
                BlockPos headPos = part == BedPart.HEAD ? pos : pos.relative(state.getValue(BedBlock.FACING));
                properties.put("pre_explosion_remove_group", "bed:" + headPos.toShortString());
                if (part == BedPart.HEAD) {
                    properties.put("explosion_radius", "5");
                    properties.put("triggerable", "true");
                    properties.put("source_key", "minecraft:bad_respawn_point");
                    properties.put("scales_with_difficulty", "true");
                }
            }
        } else if (state.getBlock() instanceof RespawnAnchorBlock) {
            boolean works = (Boolean) level.environmentAttributes().getValue(EnvironmentAttributes.RESPAWN_ANCHOR_WORKS, pos);
            if (!works && state.getValue(RespawnAnchorBlock.CHARGE) > 0) {
                properties.put("explosion_radius", "5");
                properties.put("triggerable", "true");
                properties.put("source_key", "minecraft:bad_respawn_point");
                properties.put("scales_with_difficulty", "true");
                properties.put("pre_explosion_remove_group", "anchor:" + pos.toShortString());
            }
        }

        Vec3 center = pos.getCenter();
        return new WorldSnapshot.BlockSnapshot(
            new Vec3Snapshot(center.x, center.y, center.z),
            BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
            collision,
            collisionBoxes,
            properties
        );
    }
}
