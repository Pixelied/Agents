package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.LiveConfigController;
import dev.pixelied.survival.config.PredictiveSurvivalConfigScreen;
import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.config.SurvivalConfigStore;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

final class ModMenuRuntimeValidationScenarios {
    private ModMenuRuntimeValidationScenarios() {
    }

    static void validateOptionalIntegrationAndNativeScreen(ClientGameTestContext context) {
        boolean modMenuLoaded = context.computeOnClient(minecraft -> FabricLoader.getInstance().isModLoaded("modmenu"));
        if (!modMenuLoaded) {
            context.runOnClient(minecraft -> {
                FabricLoader loader = FabricLoader.getInstance();
                if (!loader.isModLoaded("predictive_survival")) {
                    throw new AssertionError("Predictive Survival did not load without Mod Menu");
                }
                if (loader.isModLoaded("modmenu")) {
                    throw new AssertionError("withoutModMenu validation lane unexpectedly loaded Mod Menu");
                }
            });
            return;
        }

        AtomicReference<Path> configPath = new AtomicReference<>();
        context.runOnClient(minecraft -> {
            try {
                Class<?> integration = Class.forName("dev.pixelied.survival.config.PredictiveSurvivalModMenu");
                Object api = integration.getConstructor().newInstance();
                Object factory = integration.getMethod("getModConfigScreenFactory").invoke(api);
                if (factory == null) {
                    throw new AssertionError("Mod Menu config screen factory was null");
                }
                Path directory = Files.createTempDirectory("predictive-survival-modmenu-gametest");
                Path path = directory.resolve("predictive_survival.json");
                configPath.set(path);
                AtomicReference<SurvivalConfig> applied = new AtomicReference<>(SurvivalConfig.defaults());
                LiveConfigController controller = new LiveConfigController(new SurvivalConfigStore(path), applied::set);
                minecraft.setScreen(new PredictiveSurvivalConfigScreen(null, SurvivalConfig.defaults(), controller));
            } catch (Exception exception) {
                throw new AssertionError("could not open native Predictive Survival config screen", exception);
            }
        });

        context.waitFor(minecraft -> minecraft.screen instanceof PredictiveSurvivalConfigScreen);
        context.runOnClient(minecraft -> minecraft.screen.onClose());
        context.waitFor(minecraft -> minecraft.screen == null);

        Path path = configPath.get();
        if (path == null) throw new AssertionError("Mod Menu runtime fixture did not create config path");
        try {
            Files.deleteIfExists(path);
            Files.deleteIfExists(path.getParent());
        } catch (Exception exception) {
            throw new AssertionError("could not clean Mod Menu runtime fixture", exception);
        }
    }
}
