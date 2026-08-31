package dev.pixelied.survival.threat;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionPredictorOcclusionBudgetContractTest {
    @Test
    void ordinaryBlocksDoNotRebuildExplosionOcclusionViews() throws Exception {
        String source = Files.readString(Path.of(
            "src/client/java/dev/pixelied/survival/threat/ExplosionPredictor.java"
        ));

        int blockLoop = source.indexOf("for (WorldSnapshot.BlockSnapshot block : context.world().blocks())");
        int nonExplosionSkip = source.indexOf(
            "if (resolveRadius(block.properties(), context.safetyMode()) == null) continue;",
            blockLoop
        );
        int eventView = source.indexOf("withoutPreExplosionRemovedBlocks", blockLoop);

        assertTrue(blockLoop >= 0, "block explosion loop must remain explicit");
        assertTrue(nonExplosionSkip > blockLoop && nonExplosionSkip < eventView,
            "ordinary nearby blocks must be rejected before any event-specific occlusion view is built");
        assertTrue(source.contains("if (group == null || group.isBlank()) return defaultWorld;"),
            "explosive blocks without pre-removal semantics must reuse the already-built base occlusion view");
    }
}
