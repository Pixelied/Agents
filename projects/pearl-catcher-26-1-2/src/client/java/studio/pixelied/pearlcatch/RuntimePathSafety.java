package studio.pixelied.pearlcatch;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.AbstractWindCharge;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import studio.pixelied.pearlcatch.core.VanillaProjectilePhysics;

import java.util.List;
import java.util.function.Predicate;

/** Runtime-only authority checks the pure solver intentionally does not simulate. */
final class RuntimePathSafety {
    private RuntimePathSafety() {}

    static Result checkPearl(ClientLevel level, LocalPlayer player, List<Vec3> path) {
        return checkPearl(level, player, path, 0);
    }

    static Result checkPearl(ClientLevel level, LocalPlayer player, List<Vec3> path, int startingTickAge) {
        if (level == null || player == null) return Result.reject("PEARL_RUNTIME_CONTEXT_MISSING");
        ThrownEnderpearl probe = new ThrownEnderpearl(level, player, new ItemStack(Items.ENDER_PEARL));
        return check(level, player, probe, path, "PEARL", startingTickAge);
    }

    static Result checkWind(ClientLevel level, LocalPlayer player, List<Vec3> path) {
        if (level == null || player == null) return Result.reject("WIND_RUNTIME_CONTEXT_MISSING");
        Vec3 start = path == null || path.isEmpty() ? player.getEyePosition() : path.get(0);
        WindCharge probe = new WindCharge(player, level, start.x, start.y, start.z);
        return check(level, player, probe, path, "WIND", 0);
    }

    private static Result check(
            ClientLevel level, LocalPlayer player, Projectile probe, List<Vec3> path,
            String label, int startingTickAge
    ) {
        if (path == null || path.size() < 2) return Result.allow();
        Predicate<Entity> hittable = entity -> {
            if (entity == player || !entity.canBeHitByProjectile()) return false;
            if (probe instanceof AbstractWindCharge
                    && (entity instanceof AbstractWindCharge || entity.is(EntityType.END_CRYSTAL))) {
                return false;
            }
            return true;
        };
        for (int segment = 1; segment < path.size(); segment++) {
            Vec3 from = path.get(segment - 1);
            Vec3 to = path.get(segment);
            Vec3 delta = to.subtract(from);
            probe.setPos(from);

            HitResult blockHit = level.clipIncludingBorder(new ClipContext(
                    from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, probe));
            if (blockHit.getType() != HitResult.Type.MISS) {
                return Result.reject(label + "_BLOCK_OR_BORDER_AT_SEGMENT_" + segment);
            }

            AABB swept = probe.getBoundingBox().expandTowards(delta);
            String environment = unsupportedEnvironment(level, swept);
            if (environment != null) {
                return Result.reject(label + "_UNSUPPORTED_" + environment + "_AT_SEGMENT_" + segment);
            }

            AABB searchArea = swept.inflate(1.0);
            float margin = (float)VanillaProjectilePhysics.collisionMargin(startingTickAge + segment - 1);
            if (ProjectileUtil.getEntityHitResult(level, probe, from, to, searchArea, hittable, margin) != null) {
                return Result.reject(label + "_ENTITY_INTERCEPT_AT_SEGMENT_" + segment);
            }
        }
        return Result.allow();
    }

    private static String unsupportedEnvironment(ClientLevel level, AABB swept) {
        for (BlockPos pos : BlockPos.betweenClosed(swept)) {
            if (level.getBlockState(pos).is(Blocks.BUBBLE_COLUMN)) return "BUBBLE_COLUMN";
            if (level.getFluidState(pos).is(FluidTags.WATER)) return "WATER";
        }
        return null;
    }

    record Result(boolean safe, String reason) {
        static Result allow() { return new Result(true, null); }
        static Result reject(String reason) { return new Result(false, reason); }
    }
}
