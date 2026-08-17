package dev.pixelied.survival.threat;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExplosionExposureTest {
    private final ExplosionExposure exposure = new ExplosionExposure();
    private final AabbSnapshot playerBox = new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6);
    private final Vec3Snapshot center = new Vec3Snapshot(3, 0.9, 0.3);

    @Test
    void fullyOpenAndFullyBlockedExposureMatchVanillaEndpoints() {
        OcclusionView open = view(false);
        OcclusionView blocked = view(true);

        assertEquals(1f, exposure.seenPercent(playerBox, center, open), 0.0001f);
        assertEquals(0f, exposure.seenPercent(playerBox, center, blocked), 0.0001f);
    }

    @Test
    void radiusFourAtCenterWithFullExposureIsFiftySevenRawDamage() {
        assertEquals(57f, exposure.rawEntityDamage(4f, 0d, 1f), 0.0001f);
    }

    private static OcclusionView view(boolean blocked) {
        return new OcclusionView() {
            @Override
            public boolean blocksExplosionRay(Vec3Snapshot from, Vec3Snapshot to) {
                return blocked;
            }

            @Override
            public OcclusionView withCandidateBlock(CoverCandidate candidate) {
                return this;
            }
        };
    }
}
