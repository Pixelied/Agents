package dev.pixelied.survival.planner;

import dev.pixelied.survival.core.AabbSnapshot;
import dev.pixelied.survival.core.Vec3Snapshot;
import dev.pixelied.survival.threat.CoverCandidate;
import dev.pixelied.survival.threat.OcclusionView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CoverCandidateEvaluatorTest {
    @Test
    void candidateBlockCountsBeforeEntityDamageEvenIfExplosionWouldDestroyItLater() {
        CoverCandidate candidate = new CoverCandidate(new Vec3Snapshot(1, 0, 0), "minecraft:obsidian", 17);
        OcclusionView openThenCandidateBlocks = new OcclusionView() {
            @Override
            public boolean blocksExplosionRay(Vec3Snapshot from, Vec3Snapshot to) {
                return false;
            }

            @Override
            public OcclusionView withCandidateBlock(CoverCandidate ignored) {
                return new OcclusionView() {
                    @Override public boolean blocksExplosionRay(Vec3Snapshot from, Vec3Snapshot to) { return true; }
                    @Override public OcclusionView withCandidateBlock(CoverCandidate next) { return this; }
                };
            }
        };

        CoverCandidateEvaluator evaluator = new CoverCandidateEvaluator();
        AabbSnapshot box = new AabbSnapshot(0, 0, 0, 0.6, 1.8, 0.6);
        Vec3Snapshot playerPosition = new Vec3Snapshot(0, 0, 0);
        Vec3Snapshot explosionCenter = new Vec3Snapshot(3, 0.9, 0.3);

        float openDamage = evaluator.damageWithoutCandidate(box, playerPosition, explosionCenter, 6f, openThenCandidateBlocks);
        CoverCandidateEvaluator.Evaluation covered = evaluator.evaluate(
            box, playerPosition, explosionCenter, 6f, openThenCandidateBlocks, candidate
        );

        assertTrue(covered.rawDamage() < openDamage);
        assertTrue(covered.rawDamage() < 2f, "fully occluded in-range explosion should be the vanilla +1 minimum");
    }
}
