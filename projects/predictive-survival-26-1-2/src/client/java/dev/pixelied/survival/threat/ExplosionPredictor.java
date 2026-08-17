package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.core.WorldSnapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExplosionPredictor implements ThreatPredictor {
    private final ExplosionExposure exposure = new ExplosionExposure();

    @Override
    public List<ThreatEvent> predict(PredictionContext context) {
        OcclusionView world = new SnapshotOcclusionView(context.world().blocks(), List.of());
        List<ThreatEvent> events = new ArrayList<>();

        for (WorldSnapshot.EntitySnapshot entity : context.world().entities()) {
            buildEvent(
                "explosion:" + entity.id(), entity.typeKey(), entity.position(), entity.properties(),
                context, world
            ).ifPresent(events::add);
        }
        for (WorldSnapshot.BlockSnapshot block : context.world().blocks()) {
            buildEvent(
                "explosion:block:" + block.blockId() + ":" + block.position(), block.blockId(), block.position(), block.properties(),
                context, world
            ).ifPresent(events::add);
        }
        return List.copyOf(events);
    }

    private Optional<ThreatEvent> buildEvent(
        String id,
        String typeKey,
        Vec3Snapshot center,
        Map<String, String> properties,
        PredictionContext context,
        OcclusionView world
    ) {
        Float radius = parsePositiveFloat(properties.get("explosion_radius"));
        if (radius == null) return Optional.empty();

        TickWindow impact;
        Confidence confidence;
        Integer fuse = parseNonNegativeInt(properties.get("fuse_ticks"));
        if (fuse != null) {
            if (fuse > context.limits().maxProjectileHorizonTicks()) return Optional.empty();
            impact = new TickWindow(fuse, fuse);
            confidence = Confidence.EXACT;
        } else if (Boolean.parseBoolean(properties.getOrDefault("triggerable", "false"))) {
            long latest = Math.min(2, context.limits().maxProjectileHorizonTicks());
            impact = new TickWindow(0, latest);
            confidence = Confidence.POTENTIAL;
        } else {
            return Optional.empty();
        }

        float seen = exposure.seenPercent(context.player().boundingBox(), center, world);
        float raw = exposure.rawEntityDamage(radius, distance(context.player().position(), center), seen);
        if (raw <= 0f) return Optional.empty();

        EnumSet<DamageFlag> flags = EnumSet.of(DamageFlag.IS_EXPLOSION);
        String sourceKey = properties.getOrDefault("source_key", "minecraft:explosion");
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            DamageRange.exact(raw), flags,
            Boolean.parseBoolean(properties.getOrDefault("scales_with_difficulty", "false")),
            1f, false, Optional.of(center), sourceKey
        );

        return Optional.of(new ThreatEvent(
            id,
            ThreatKind.EXPLOSION,
            impact,
            damage,
            confidence,
            Optional.of(center),
            Optional.of(center),
            true,
            !Boolean.parseBoolean(properties.getOrDefault("bypasses_shield", "false")),
            true,
            false
        ));
    }

    private static Float parsePositiveFloat(String value) {
        if (value == null) return null;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed > 0f ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Integer parseNonNegativeInt(String value) {
        if (value == null) return null;
        try {
            int parsed = Integer.parseInt(value);
            return parsed >= 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static double distance(Vec3Snapshot a, Vec3Snapshot b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static final class SnapshotOcclusionView implements OcclusionView {
        private final List<WorldSnapshot.BlockSnapshot> blocks;
        private final List<CoverCandidate> candidates;

        private SnapshotOcclusionView(List<WorldSnapshot.BlockSnapshot> blocks, List<CoverCandidate> candidates) {
            this.blocks = List.copyOf(blocks);
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public boolean blocksExplosionRay(Vec3Snapshot from, Vec3Snapshot to) {
            for (WorldSnapshot.BlockSnapshot block : blocks) {
                if (block.collision() && intersectsUnitCube(from, to, block.position())) return true;
            }
            for (CoverCandidate candidate : candidates) {
                if (intersectsUnitCube(from, to, candidate.blockPos())) return true;
            }
            return false;
        }

        @Override
        public OcclusionView withCandidateBlock(CoverCandidate candidate) {
            List<CoverCandidate> next = new ArrayList<>(candidates);
            next.add(candidate);
            return new SnapshotOcclusionView(blocks, next);
        }

        private static boolean intersectsUnitCube(Vec3Snapshot from, Vec3Snapshot to, Vec3Snapshot block) {
            double minX = Math.floor(block.x());
            double minY = Math.floor(block.y());
            double minZ = Math.floor(block.z());
            double maxX = minX + 1.0;
            double maxY = minY + 1.0;
            double maxZ = minZ + 1.0;

            double[] range = {0.0, 1.0};
            return slab(from.x(), to.x() - from.x(), minX, maxX, range)
                && slab(from.y(), to.y() - from.y(), minY, maxY, range)
                && slab(from.z(), to.z() - from.z(), minZ, maxZ, range);
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
}
