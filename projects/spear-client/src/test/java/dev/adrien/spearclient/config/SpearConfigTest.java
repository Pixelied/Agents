package dev.adrien.spearclient.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SpearConfigTest {
    @TempDir
    Path tempDir;

    @Test
    void defaultsAreConservative() {
        SpearConfig config = SpearConfig.defaults();

        assertFalse(config.oneTap().enabled());
        assertFalse(config.lungeBoost().enabled());
        assertFalse(config.infiniteReach().enabled());
        assertFalse(config.debug());
        assertTrue(config.infiniteReach().teamCheck());
    }

    @Test
    void loadInvalidJsonFallsBackToDefaults() throws Exception {
        Path file = tempDir.resolve("spearclient.json");
        Files.writeString(file, "{broken");

        assertEquals(SpearConfig.defaults(), new ConfigStore(file).load());
    }

    @Test
    void toggledValuesPersistExactly() throws Exception {
        Path file = tempDir.resolve("spearclient.json");
        ConfigStore store = new ConfigStore(file);
        SpearConfig changed = new SpearConfig(
            new SpearConfig.OneTapConfig(true, SpearConfig.OneTapMode.SMART),
            new SpearConfig.LungeConfig(true, SpearConfig.LungeMode.SMART),
            new SpearConfig.ReachConfig(true, SpearConfig.ReachMode.SMART, false),
            true
        );

        store.save(changed);

        assertEquals(changed, store.load());
    }
}
