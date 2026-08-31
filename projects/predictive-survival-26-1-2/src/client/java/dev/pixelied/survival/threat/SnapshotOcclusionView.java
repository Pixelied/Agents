package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SnapshotOcclusionView implements OcclusionView {
    private static final double VANILLA_TEST_POINT_SCALE = 0.001d;
    private static final double VANILLA_MIN_RAY_LENGTH_SQUARED = 1.0E-7d;

    private final List<AabbSnapshot> collisionBoxes;
    private final List<CoverCandidate> candidates;

    public SnapshotOcclusionView(List<WorldSnapshot.BlockSnapshot> blocks) {
        this(flattenCollisionBoxes(blocks), List.of(), true);
    }

    SnapshotOcclusionView(List<WorldSnapshot.BlockSnapshot> blocks, List<CoverCandidate> candidates) {
        this(flattenCollisionBoxes(blocks), candidates, true);
    }

    private SnapshotOcclusionView(
        List<AabbSnapshot> collisionBoxes,
        List<CoverCandidate> candidates,
        boolean precomputed
    ) {
        this.collisionBoxes = List.copyOf(Objects.requireNonNull(collisionBoxes, "collisionBoxes"));
        this.candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
    }

    @Override
    public boolean blocksExplosionRay(Vec3Snapshot from, Vec3Snapshot to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        for (AabbSnapshot collisionBox : collisionBoxes) {
            if (intersects(from, to, collisionBox)) return true;
        }
        for (CoverCandidate candidate : candidates) {
            if (intersects(from, to, unitCube(candidate.blockPos()))) return true;
        }
        return false;
    }

    @Override
    public OcclusionView withCandidateBlock(CoverCandidate candidate) {
        List<CoverCandidate> next = new ArrayList<>(candidates);
        next.add(Objects.requireNonNull(candidate, "candidate"));
        return new SnapshotOcclusionView(collisionBoxes, next, true);
    }

    private static List<AabbSnapshot> flattenCollisionBoxes(List<WorldSnapshot.BlockSnapshot> blocks) {
        List<AabbSnapshot> result = new ArrayList<>();
        for (WorldSnapshot.BlockSnapshot block : Objects.requireNonNull(blocks, "blocks")) {
            if (!block.collision()) continue;
            if (!block.collisionBoxes().isEmpty()) {
                result.addAll(block.collisionBoxes());
            } else if (ExplosionPredictor.canUseUnitCubeOcclusion(block)) {
                result.add(unitCube(block.position()));
            }
        }
        return List.copyOf(result);
    }

    private static AabbSnapshot unitCube(Vec3Snapshot block) {
        double minX = Math.floor(block.x());
        double minY = Math.floor(block.y());
        double minZ = Math.floor(block.z());
        return new AabbSnapshot(minX, minY, minZ, minX + 1.0, minY + 1.0, minZ + 1.0);
    }

    private static boolean intersects(Vec3Snapshot from, Vec3Snapshot to, AabbSnapshot box) {
        double dx = to.x() - from.x();
        double dy = to.y() - from.y();
        double dz = to.z() - from.z();
        if (dx * dx + dy * dy + dz * dz < VANILLA_MIN_RAY_LENGTH_SQUARED) return false;

        Vec3Snapshot testPoint = new Vec3Snapshot(
            from.x() + dx * VANILLA_TEST_POINT_SCALE,
            from.y() + dy * VANILLA_TEST_POINT_SCALE,
            from.z() + dz * VANILLA_TEST_POINT_SCALE
        );
        if (contains(testPoint, box)) return true;

        double[] range = {0.0, 1.0};
        boolean intersects = slab(from.x(), dx, box.minX(), box.maxX(), range)
            && slab(from.y(), dy, box.minY(), box.maxY(), range)
            && slab(from.z(), dz, box.minZ(), box.maxZ(), range);
        return intersects && range[0] > 0.0 && range[0] < 1.0;
    }

    private static boolean contains(Vec3Snapshot point, AabbSnapshot box) {
        return point.x() >= box.minX() && point.x() < box.maxX()
            && point.y() >= box.minY() && point.y() < box.maxY()
            && point.z() >= box.minZ() && point.z() < box.maxZ();
    }

    private static boolean slab(double origin, double direction, double min, double max, double[] range) {
        if (Math.abs(direction) < 1.0E-12) return origin >= min && origin <= max;
        double t1 = (min - origin) / direction;
        double t2 = (max - origin) / direction;
        if (t1 > t2) {
            double tmp = t1;
            t1 = t2;
            t2 = tmp;
        }
        range[0] = Math.max(range[0], t1);
        range[1] = Math.min(range[1], t2);
        return range[0] <= range[1];
    }
}
