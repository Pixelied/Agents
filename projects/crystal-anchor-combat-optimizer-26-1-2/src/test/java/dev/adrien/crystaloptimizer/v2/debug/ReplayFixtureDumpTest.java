package dev.adrien.crystaloptimizer.v2.debug;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

/** Temporary materialization helper; removed once canonical JSON resources are committed. */
final class ReplayFixtureDumpTest {
    @Test
    void dumpCandidateBudgetAnchorStarvation() throws Exception {
        dump("candidate-budget-anchor-starvation.json");
    }

    @Test
    void dumpFourthTargetLethal() throws Exception {
        dump("fourth-target-lethal.json");
    }

    @Test
    void dumpProtectedWindowSingleApplication() throws Exception {
        dump("protected-window-single-application.json");
    }

    @Test
    void dumpBreakRemovePlaceContinuation() throws Exception {
        dump("break-remove-place-continuation.json");
    }

    @Test
    void dumpPredictedStrafePlacement() throws Exception {
        dump("predicted-strafe-placement.json");
    }

    private static void dump(String name) throws Exception {
        ReplayFixture fixture = V3ReplayFixtures.checkedInFixtures().get(name);
        if (fixture == null) {
            throw new AssertionError("missing generated fixture: " + name);
        }
        ReplayCodec codec = new ReplayCodec();
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
            gzip.write(codec.encode(fixture));
        }
        throw new AssertionError(
            "REPLAY_GZ_B64 " + name + " "
                + Base64.getEncoder().encodeToString(compressed.toByteArray())
        );
    }
}
