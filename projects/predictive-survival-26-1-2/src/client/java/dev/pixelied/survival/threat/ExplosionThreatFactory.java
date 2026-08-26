package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Confidence;
import dev.pixelied.survival.core.DamageRange;
import dev.pixelied.survival.core.PredictionContext;
import dev.pixelied.survival.core.TickWindow;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.damage.DamageFlag;
import dev.pixelied.survival.damage.DamageSourceSnapshot;
import dev.pixelied.survival.timeline.ThreatEvent;
import dev.pixelied.survival.timeline.ThreatKind;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;

/** One shared vanilla explosion raw-damage path for observed and hypothetical threats. */
public final class ExplosionThreatFactory {
    private final ExplosionExposure exposure;

    public ExplosionThreatFactory() {
        this(new ExplosionExposure());
    }

    ExplosionThreatFactory(ExplosionExposure exposure) {
        this.exposure = Objects.requireNonNull(exposure, "exposure");
    }

    public Optional<ThreatEvent> create(
        String id,
        TickWindow impact,
        Confidence confidence,
        ExplosionSpec spec,
        PredictionContext context,
        OcclusionView world
    ) {
        return createInternal(id, impact, confidence, spec, context, world, null);
    }

    /**
     * Triggerable explosions can be accepted on any tick in the reaction window. Preserve the
     * existing conservative motion envelope while sharing the same per-tick explosion math.
     */
    public Optional<ThreatEvent> createProjected(
        String id,
        TickWindow impact,
        Confidence confidence,
        ExplosionSpec spec,
        Vec3Snapshot sourceVelocity,
        PredictionContext context,
        OcclusionView world
    ) {
        return createInternal(
            id, impact, confidence, spec, context, world,
            Objects.requireNonNull(sourceVelocity, "sourceVelocity")
        );
    }

    private Optional<ThreatEvent> createInternal(
        String id,
        TickWindow impact,
        Confidence confidence,
        ExplosionSpec spec,
        PredictionContext context,
        OcclusionView world,
        Vec3Snapshot sourceVelocity
    ) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(impact, "impact");
        Objects.requireNonNull(confidence, "confidence");
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(world, "world");

        DamageRange raw = sourceVelocity == null
            ? damageAt(spec, spec.center(), context.player().position(), context.player().boundingBox(), world)
            : projectedDamageEnvelope(spec, sourceVelocity, impact.latest(), context, world);
        if (raw.max() <= 0f) return Optional.empty();

        DamageSourceSnapshot damage = new DamageSourceSnapshot(
            raw,
            EnumSet.of(DamageFlag.IS_EXPLOSION),
            spec.scalesWithDifficulty(),
            1f,
            false,
            Optional.of(spec.center()),
            spec.sourceKey()
        );
        return Optional.of(new ThreatEvent(
            id,
            ThreatKind.EXPLOSION,
            impact,
            damage,
            confidence,
            Optional.of(spec.center()),
            Optional.of(spec.center()),
            true,
            spec.blockable(),
            true,
            false
        ));
    }

    private DamageRange projectedDamageEnvelope(
        ExplosionSpec spec,
        Vec3Snapshot sourceVelocity,
        long latestTick,
        PredictionContext context,
        OcclusionView world
    ) {
        float rawMin = Float.POSITIVE_INFINITY;
        float rawMax = 0f;
        for (long tick = 0; tick <= latestTick; tick++) {
            Vec3Snapshot playerOffset = scale(context.player().velocity(), tick);
            Vec3Snapshot sourceOffset = scale(sourceVelocity, tick);
            DamageRange atTick = damageAt(
                spec,
                add(spec.center(), sourceOffset),
                add(context.player().position(), playerOffset),
                translate(context.player().boundingBox(), playerOffset),
                world
            );
            rawMin = Math.min(rawMin, atTick.min());
            rawMax = Math.max(rawMax, atTick.max());
        }
        if (!Float.isFinite(rawMin)) rawMin = 0f;
        return new DamageRange(rawMin, rawMax);
    }

    private DamageRange damageAt(
        ExplosionSpec spec,
        Vec3Snapshot center,
        Vec3Snapshot playerPosition,
        AabbSnapshot playerBox,
        OcclusionView world
    ) {
        float seen = exposure.seenPercent(playerBox, center, world);
        double distance = distance(playerPosition, center);
        return new DamageRange(
            exposure.rawEntityDamage(spec.radiusMin(), distance, seen),
            exposure.rawEntityDamage(spec.radiusMax(), distance, seen)
        );
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

    private static double distance(Vec3Snapshot a, Vec3Snapshot b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
