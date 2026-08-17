package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.Vec3Snapshot;

public interface OcclusionView {
    boolean blocksExplosionRay(Vec3Snapshot from, Vec3Snapshot to);
    OcclusionView withCandidateBlock(CoverCandidate candidate);
}
