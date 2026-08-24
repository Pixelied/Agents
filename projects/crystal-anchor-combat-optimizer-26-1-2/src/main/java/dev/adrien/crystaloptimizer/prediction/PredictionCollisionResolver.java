package dev.adrien.crystaloptimizer.prediction;

import dev.adrien.crystaloptimizer.world.CombatRegion;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Pure copy of the axis-resolution core used by Entity.collide in 26.1.2. */
public final class PredictionCollisionResolver {
    private static final double COLLISION_EPSILON = 1.0E-7;

    public CollisionMoveResult move(AABB box, Vec3 requestedDelta, CombatRegion geometry) {
        Objects.requireNonNull(box, "box");
        Objects.requireNonNull(requestedDelta, "requestedDelta");
        Objects.requireNonNull(geometry, "geometry");

        List<VoxelShape> colliders = worldColliders(geometry);
        if (colliders.isEmpty() || requestedDelta.lengthSqr() == 0.0) {
            return new CollisionMoveResult(
                requestedDelta,
                requestedDelta,
                box.move(requestedDelta),
                false,
                false,
                false
            );
        }

        Vec3 resolved = Vec3.ZERO;
        for (Direction.Axis axis : Direction.axisStepOrder(requestedDelta)) {
            double axisMovement = requestedDelta.get(axis);
            if (axisMovement == 0.0) {
                continue;
            }
            double collision = Shapes.collide(
                axis,
                box.move(resolved),
                colliders,
                axisMovement
            );
            resolved = resolved.with(axis, collision);
        }

        return new CollisionMoveResult(
            requestedDelta,
            resolved,
            box.move(resolved),
            differs(requestedDelta.x, resolved.x),
            differs(requestedDelta.y, resolved.y),
            differs(requestedDelta.z, resolved.z)
        );
    }

    private static List<VoxelShape> worldColliders(CombatRegion geometry) {
        LinkedHashSet<BlockPos> positions = new LinkedHashSet<>(geometry.states().keySet());
        positions.addAll(geometry.collisionShapes().keySet());
        ArrayList<VoxelShape> colliders = new ArrayList<>(positions.size());
        for (BlockPos pos : positions) {
            VoxelShape local = geometry.collisionShape(pos);
            if (local.isEmpty()) {
                continue;
            }
            colliders.add(local.move(pos.getX(), pos.getY(), pos.getZ()));
        }
        return List.copyOf(colliders);
    }

    private static boolean differs(double requested, double resolved) {
        return Math.abs(requested - resolved) > COLLISION_EPSILON;
    }
}
