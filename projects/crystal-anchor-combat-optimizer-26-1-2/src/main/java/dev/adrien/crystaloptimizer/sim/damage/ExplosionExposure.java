package dev.adrien.crystaloptimizer.sim.damage;

import dev.adrien.crystaloptimizer.world.BlockView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.BlockPos.MutableBlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public final class ExplosionExposure {
    public static float seenPercent(Vec3 center, AABB box, BlockView blocks) {
        double xs = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double ys = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double zs = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double xOffset = (1.0 - Math.floor(1.0 / xs) * xs) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zs) * zs) / 2.0;
        if (xs < 0.0 || ys < 0.0 || zs < 0.0) {
            return 0.0f;
        }

        int hits = 0;
        int count = 0;
        for (double xx = 0.0; xx <= 1.0; xx += xs) {
            for (double yy = 0.0; yy <= 1.0; yy += ys) {
                for (double zz = 0.0; zz <= 1.0; zz += zs) {
                    double x = lerp(xx, box.minX, box.maxX);
                    double y = lerp(yy, box.minY, box.maxY);
                    double z = lerp(zz, box.minZ, box.maxZ);
                    Vec3 from = new Vec3(x + xOffset, y, z + zOffset);
                    if (rayMissesBlocks(from, center, blocks)) {
                        hits++;
                    }
                    count++;
                }
            }
        }
        return count == 0 ? 0.0f : (float) hits / count;
    }

    static boolean rayMissesBlocks(Vec3 from, Vec3 to, BlockView blocks) {
        if (from.equals(to)) {
            return true;
        }

        double toX = lerp(-1.0E-7, to.x, from.x);
        double toY = lerp(-1.0E-7, to.y, from.y);
        double toZ = lerp(-1.0E-7, to.z, from.z);
        double fromX = lerp(-1.0E-7, from.x, to.x);
        double fromY = lerp(-1.0E-7, from.y, to.y);
        double fromZ = lerp(-1.0E-7, from.z, to.z);
        int currentBlockX = floor(fromX);
        int currentBlockY = floor(fromY);
        int currentBlockZ = floor(fromZ);
        MutableBlockPos pos = new MutableBlockPos(currentBlockX, currentBlockY, currentBlockZ);
        if (hitsBlock(from, to, pos, blocks)) {
            return false;
        }

        double dx = toX - fromX;
        double dy = toY - fromY;
        double dz = toZ - fromZ;
        int signX = sign(dx);
        int signY = sign(dy);
        int signZ = sign(dz);
        double tDeltaX = signX == 0 ? Double.MAX_VALUE : signX / dx;
        double tDeltaY = signY == 0 ? Double.MAX_VALUE : signY / dy;
        double tDeltaZ = signZ == 0 ? Double.MAX_VALUE : signZ / dz;
        double tX = tDeltaX * (signX > 0 ? 1.0 - frac(fromX) : frac(fromX));
        double tY = tDeltaY * (signY > 0 ? 1.0 - frac(fromY) : frac(fromY));
        double tZ = tDeltaZ * (signZ > 0 ? 1.0 - frac(fromZ) : frac(fromZ));

        while (tX <= 1.0 || tY <= 1.0 || tZ <= 1.0) {
            if (tX < tY) {
                if (tX < tZ) {
                    currentBlockX += signX;
                    tX += tDeltaX;
                } else {
                    currentBlockZ += signZ;
                    tZ += tDeltaZ;
                }
            } else if (tY < tZ) {
                currentBlockY += signY;
                tY += tDeltaY;
            } else {
                currentBlockZ += signZ;
                tZ += tDeltaZ;
            }

            if (hitsBlock(from, to, pos.set(currentBlockX, currentBlockY, currentBlockZ), blocks)) {
                return false;
            }
        }
        return true;
    }

    private static boolean hitsBlock(Vec3 from, Vec3 to, BlockPos pos, BlockView blocks) {
        VoxelShape shape = blocks.collisionShape(pos);
        return !shape.isEmpty() && shape.clip(from, to, pos) != null;
    }

    private static double lerp(double delta, double start, double end) {
        return start + delta * (end - start);
    }

    private static int floor(double value) {
        int truncated = (int) value;
        return value < truncated ? truncated - 1 : truncated;
    }

    private static int sign(double value) {
        return value > 0.0 ? 1 : value < 0.0 ? -1 : 0;
    }

    private static double frac(double value) {
        return value - Math.floor(value);
    }

    private ExplosionExposure() {
    }
}
