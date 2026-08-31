package dev.pixelied.survival.threat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotOcclusionHotPathContractTest {
    @Test
    void explosionRayHotPathUsesPrecomputedCollisionBoxes() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/threat/SnapshotOcclusionView.java"
        ));

        int methodStart = source.indexOf("public boolean blocksExplosionRay");
        int methodEnd = source.indexOf("@Override\n    public OcclusionView withCandidateBlock", methodStart);
        String hotPath = source.substring(methodStart, methodEnd);

        assertTrue(source.contains("List<AabbSnapshot> collisionBoxes"),
            "occlusion view must precompute exact collision AABBs once at construction");
        assertTrue(hotPath.contains("for (AabbSnapshot collisionBox : collisionBoxes)"),
            "every explosion ray must iterate precomputed collision AABBs directly");
        assertFalse(hotPath.contains("block.collisionBoxes()"),
            "ray hot path must not re-expand block collision components");
        assertFalse(hotPath.contains("canUseUnitCubeOcclusion"),
            "ray hot path must not re-read block metadata or recreate unit-cube collision boxes");
    }
}
