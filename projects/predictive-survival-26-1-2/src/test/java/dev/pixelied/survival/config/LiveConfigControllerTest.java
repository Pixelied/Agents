package dev.pixelied.survival.config;

import dev.pixelied.survival.planner.SafetyMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LiveConfigControllerTest {
    @TempDir Path tempDir;

    @Test
    void persistsBeforeApplyingLiveConfig() throws Exception {
        Path path = tempDir.resolve("predictive_survival.json");
        SurvivalConfigStore store = new SurvivalConfigStore(path);
        AtomicReference<SurvivalConfig> live = new AtomicReference<>(SurvivalConfig.defaults());
        LiveConfigController controller = new LiveConfigController(store, live::set);
        SurvivalConfig edited = new SurvivalConfig(SafetyMode.BALANCED, false, true, false, true);

        controller.apply(edited);

        assertEquals(edited, store.load());
        assertEquals(edited, live.get());
    }

    @Test
    void failedPersistenceNeverPartiallyAppliesLiveConfig() throws Exception {
        Path parentFile = tempDir.resolve("not-a-directory");
        Files.writeString(parentFile, "x");
        SurvivalConfigStore store = new SurvivalConfigStore(parentFile.resolve("config.json"));
        AtomicReference<SurvivalConfig> live = new AtomicReference<>(SurvivalConfig.defaults());
        LiveConfigController controller = new LiveConfigController(store, live::set);
        SurvivalConfig edited = new SurvivalConfig(SafetyMode.EXPERIMENTAL, false, true, false, true);

        assertThrows(java.io.IOException.class, () -> controller.apply(edited));

        assertEquals(SurvivalConfig.defaults(), live.get());
    }
}
