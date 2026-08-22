package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
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
                "explosion:" + entity.id(), entity.typeKey(), entity.position(), entity.velocity(), entity.properties(),
                context, world
            ).ifPresent(events::add);
        }
        for (WorldSnapshot.BlockSnapshot block : context.world().blocks()) {
            OcclusionView eventWorld = withoutPreExplosionRemovedBlocks(context.world().blocks(), block);
            buildEvent(
                "explosion:block:" + block.blockId() + ":" + block.position(), block.blockId(), block.position(),
                new Vec3Snapshot(0, 0, 0), block.properties(), context, eventWorld
            ).ifPresent(events::add);
        }
        return List.copyOf(events);
    }

    private Optional<ThreatEvent> buildEvent(
        String id,
        String typeKey,
        Vec3Snapshot center,
        Vec3Snapshot sourceVelocity,
        Map<String, String> properties,
        PredictionContext context,
        OcclusionView world
    ) {
        RadiusRange radius = parseRadiusRange(properties);
        if (radius == null) return Optional.empty();

        TickWindow impact;
        Confidence confidence;
        boolean triggerable = Boolean.parseBoolean(properties.getOrDefault("triggerable", "false"));
        Integer fuse = parseNonNegativeInt(properties.get("fuse_ticks"));
        if (fuse != null) {
            if (fuse > context.limits().maxProjectileHorizonTicks()) return Optional.empty();
            impact = new TickWindow(fuse, fuse);
            confidence = Confidence.EXACT;
        } else {
            Integer fuseMin = parseNonNegativeInt(properties.get("fuse_ticks_min"));
            Integer fuseMax = parseNonNegativeInt(properties.get("fuse_ticks_max"));
            if (fuseMin != null && fuseMax != null) {
                if (fuseMin > fuseMax || fuseMin > context.limits().maxProjectileHorizonTicks()) return Optional.empty();
                long latest = Math.min(fuseMax, context.limits().maxProjectileHorizonTicks());
                impact = new TickWindow(fuseMin, latest);
                confidence = Confidence.BOUNDED;
            } else if (triggerable) {
                long reactionTicks = Math.max(
                    0L,
                    context.timing().nextPacketProcessingWindow().latest() - context.timing().clientTick()
                );
                long latest = Math.min(reactionTicks, context.limits().maxProjectileHorizonTicks());
                impact = new TickWindow(0, latest);
                confidence = Confidence.POTENTIAL;
            } else {
                return Optional.empty();
            }
        }
        if (radius.bounded()) confidence = lessCertain(confidence, Confidence.BOUNDED);

        DamageRange raw = triggerable
            ? triggerableDamageEnvelope(radius, center, sourceVelocity, impact.latest(), context, world)
            : damageAt(radius, center, context.player().position(), context.player().boundingBox(), world);
        float rawMin = raw.min();
        float rawMax = raw.max();
        if (rawMax <= 0f) return Optional.empty();

        EnumSet<DamageFlag> flags = EnumSet.of(DamageFlag.IS_EXPLOSION);
        String sourceKey = properties.getOrDefault("source_key", "minecraft:explosion");
        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            new DamageRange(rawMin, rawMax), flags,
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

    private DamageRange triggerableDamageEnvelope(
        RadiusRange radius,
        Vec3Snapshot center,
        Vec3Snapshot sourceVelocity,
        long reactionTicks,
        PredictionContext context,
        OcclusionView world
    ) {
        float rawMin = Float.POSITIVE_INFINITY;
        float rawMax = 0f;
        for (long tick = 0; tick <= reactionTicks; tick++) {
            Vec3Snapshot playerOffset = scale(context.player().velocity(), tick);
            Vec3Snapshot sourceOffset = scale(sourceVelocity, tick);
            Vec3Snapshot projectedPlayer = add(context.player().position(), playerOffset);
            AabbSnapshot projectedBox = translate(context.player().boundingBox(), playerOffset);
            Vec3Snapshot projectedCenter = add(center, sourceOffset);
            DamageRange atTick = damageAt(radius, projectedCenter, projectedPlayer, projectedBox, world);
            rawMin = Math.min(rawMin, atTick.min());
            rawMax = Math.max(rawMax, atTick.max());
        }
        if (!Float.isFinite(rawMin)) rawMin = 0f;
        return new DamageRange(rawMin, rawMax);
    }

    private DamageRange damageAt(
        RadiusRange radius,
        Vec3Snapshot center,
        Vec3Snapshot playerPosition,
        AabbSnapshot playerBox,
        OcclusionView world
    ) {
        float seen = exposure.seenPercent(playerBox, center, world);
        double distance = distance(playerPosition, center);
        return new DamageRange(
            exposure.rawEntityDamage(radius.min(), distance, seen),
            exposure.rawEntityDamage(radius.max(), distance, seen)
        );
    }

    private static OcclusionView withoutPreExplosionRemovedBlocks(
        List<WorldSnapshot.BlockSnapshot> blocks,
        WorldSnapshot.BlockSnapshot source
    ) {
        String group = source.properties().get("pre_explosion_remove_group");
        if (group == null || group.isBlank()) return new SnapshotOcclusionView(blocks, List.of());
        List<WorldSnapshot.BlockSnapshot> filtered = blocks.stream()
            .filter(block -> !group.equals(block.properties().get("pre_explosion_remove_group")))
            .toList();
        return new SnapshotOcclusionView(filtered, List.of());
    }

    private static Vec3Snapshot scale(Vec3Snapshot vector, long ticks) {
        return new Vec3Snapshot(vector.x() * ticks, vector.y() * ticks, vector.z() * ticks);
    }

    private static Vec3Snapshot add(Vec3Snapshot vector, Vec3Snapshot offset) {
        return new Vec3Snapshot(vector.x() + offset.x(), vector.y() + offset.y(), vector.z() + offset.z());
    }

    private static AabbSnapshot translate(AabbSnapshot box, Vec3Snapshot offset) {
        return new AabbSnapshot(
            box.minX() + offset.x(), box.minY() + offset.y(), box.minZ() + offset.z(),
            box.maxX() + offset.x(), box.maxY() + offset.y(), box.maxZ() + offset.z()
        );
    }

    static boolean canUseUnitCubeOcclusion(WorldSnapshot.BlockSnapshot block) {
        return block.collision()
            && Boolean.parseBoolean(block.properties().getOrDefault("full_collision_cube", "false"));
    }

    private static RadiusRange parseRadiusRange(Map<String, String> properties) {
        Float exact = parsePositiveFloat(properties.get("explosion_radius"));
        if (exact != null) return new RadiusRange(exact, exact);

        Float min = parseNonNegativeFloat(properties.get("explosion_radius_min"));
        Float max = parsePositiveFloat(properties.get("explosion_radius_max"));
        if (min == null || max == null || min > max) return null;
        return new RadiusRange(min, max);
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

    private static Float parseNonNegativeFloat(String value) {
        if (value == null) return null;
        try {
            float parsed = Float.parseFloat(value);
            return Float.isFinite(parsed) && parsed >= 0f ? parsed : null;
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

    private static Confidence lessCertain(Confidence first, Confidence second) {
        return first.ordinal() >= second.ordinal() ? first : second;
    }

    private static double distance(Vec3Snapshot a, Vec3Snapshot b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private record RadiusRange(float min, float max) {
        private boolean bounded() {
            return Float.compare(min, max) != 0;
        }
    }

    private static final class SnapshotOcclusionView implements OcclusionView {
        private static final double RAY_ORIGIN_EPSILON = 1.0E-9;

        private final List<WorldSnapshot.BlockSnapshot> blocks;
        private final List<CoverCandidate> candidates;

        private SnapshotOcclusionView(List<WorldSnapshot.BlockSnapshot> blocks, List<CoverCandidate> candidates) {
            this.blocks = List.copyOf(blocks);
            this.candidates = List.copyOf(candidates);
        }

        @Override
        public boolean blocksExplosionRay(Vec3Snapshot from, Vec3Snapshot to) {
            for (WorldSnapshot.BlockSnapshot block : blocks) {
                if (canUseUnitCubeOcclusion(block) && intersectsUnitCube(from, to, block.position())) return true;
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
            boolean intersects = slab(from.x(), to.x() - from.x(), minX, maxX, range)
                && slab(from.y(), to.y() - from.y(), minY, maxY, range)
                && slab(from.z(), to.z() - from.z(), minZ, maxZ, range);
            return intersects && range[1] > RAY_ORIGIN_EPSILON;
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
