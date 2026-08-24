package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.threat.CoverCandidate;
import dev.pixelied.survival.threat.ExplosionExposure;
import dev.pixelied.survival.threat.OcclusionView;

import java.util.Objects;

public final class CoverCandidateEvaluator {
    private final ExplosionExposure exposure = new ExplosionExposure();

    public float damageWithoutCandidate(
        AabbSnapshot target,
        Vec3Snapshot entityPosition,
        Vec3Snapshot center,
        float radius,
        OcclusionView world
    ) {
        float seen = exposure.seenPercent(target, center, world);
        return exposure.rawEntityDamage(radius, distance(entityPosition, center), seen);
    }

    public Evaluation evaluate(
        AabbSnapshot target,
        Vec3Snapshot entityPosition,
        Vec3Snapshot center,
        float radius,
        OcclusionView world,
        CoverCandidate candidate
    ) {
        Objects.requireNonNull(candidate, "candidate");
        OcclusionView withCover = Objects.requireNonNull(world, "world").withCandidateBlock(candidate);
        float seen = exposure.seenPercent(target, center, withCover);
        float raw = exposure.rawEntityDamage(radius, distance(entityPosition, center), seen);
        return new Evaluation(candidate, seen, raw);
    }

    private static double distance(Vec3Snapshot a, Vec3Snapshot b) {
        double dx = a.x() - b.x();
        double dy = a.y() - b.y();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public record Evaluation(CoverCandidate candidate, float exposure, float rawDamage) {
    }
}
