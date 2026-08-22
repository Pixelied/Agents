package dev.adrien.crystaloptimizer.v2.debug;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;

final class ReplayFixtureDumpTest {
    @Test
    void dumpCheckedInFixturesForRepositoryMaterialization() throws Exception {
        ReplayCodec codec = new ReplayCodec();
        for (var entry : V3ReplayFixtures.checkedInFixtures().entrySet()) {
            ByteArrayOutputStream compressed = new ByteArrayOutputStream();
            try (GZIPOutputStream gzip = new GZIPOutputStream(compressed)) {
                gzip.write(codec.encode(entry.getValue()));
            }
            System.out.println(
                "REPLAY_GZ_B64 " + entry.getKey() + " "
                    + Base64.getEncoder().encodeToString(compressed.toByteArray())
            );
        }
    }
}
