package dev.adrien.crystaloptimizer.client;

import dev.adrien.crystaloptimizer.client.config.OptimizerConfigService;
import dev.adrien.crystaloptimizer.config.OptimizerConfig;
import dev.adrien.crystaloptimizer.config.OptimizerStrategy;
import dev.adrien.crystaloptimizer.execution.RotationMode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptimizerConfigServiceTest {
    @Test
    void applyValidatesPersistsAndPublishesOneSnapshot() throws Exception {
        Path dir = Files.createTempDirectory("crystaloptimizer-config");
        OptimizerConfigService service = OptimizerConfigService.forDirectory(dir);
        OptimizerConfig changed = new OptimizerConfig(
            true,
            OptimizerStrategy.AGGRESSIVE,
            10.0,
            5.0f,
            10.0f,
            7.0f,
            true,
            true,
            false,
            RotationMode.INSTANT,
            true
        );

        service.apply(changed);

        assertEquals(changed, service.current());
        assertTrue(service.revision() > 0L);
        assertEquals(changed, OptimizerConfigService.forDirectory(dir).current());
        assertTrue(Files.exists(dir.resolve("crystaloptimizer.json")));
    }

    @Test
    void invalidConfigIsQuarantinedAndDefaultsAreRewritten() throws Exception {
        Path dir = Files.createTempDirectory("crystaloptimizer-invalid-config");
        Files.writeString(dir.resolve("crystaloptimizer.json"), "{ definitely not valid json");

        OptimizerConfigService service = OptimizerConfigService.forDirectory(dir);

        assertEquals(OptimizerConfig.defaults(), service.current());
        assertTrue(Files.exists(dir.resolve("crystaloptimizer.json.invalid")));
        assertTrue(Files.exists(dir.resolve("crystaloptimizer.json")));
    }
}
