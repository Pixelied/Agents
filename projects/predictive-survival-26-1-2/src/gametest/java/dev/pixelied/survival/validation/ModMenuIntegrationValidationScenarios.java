package dev.pixelied.survival.validation;

import dev.pixelied.survival.config.PredictiveSurvivalConfigScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

import java.lang.reflect.Method;

final class ModMenuIntegrationValidationScenarios {
    private ModMenuIntegrationValidationScenarios() {
    }

    static void validatePresentIntegrationAndNativeScreen(ClientGameTestContext context) {
        if (!FabricLoader.getInstance().isModLoaded("modmenu")) return;

        context.runOnClient(minecraft -> {
            Object entrypoint = FabricLoader.getInstance()
                .getEntrypointContainers("modmenu", Object.class)
                .stream()
                .map(container -> container.getEntrypoint())
                .filter(candidate -> candidate.getClass().getName().equals("dev.pixelied.survival.config.PredictiveSurvivalModMenu"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Mod Menu did not discover Predictive Survival's modmenu entrypoint"));

            try {
                Object factory = entrypoint.getClass().getMethod("getModConfigScreenFactory").invoke(entrypoint);
                if (factory == null) throw new AssertionError("Mod Menu config-screen factory was null");
                Method create = java.util.Arrays.stream(factory.getClass().getMethods())
                    .filter(method -> method.getName().equals("create"))
                    .filter(method -> method.getParameterCount() == 1)
                    .filter(method -> Screen.class.isAssignableFrom(method.getParameterTypes()[0]))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Mod Menu config-screen factory exposes no Screen create method"));
                Screen parent = minecraft.screen;
                Object created = create.invoke(factory, parent);
                if (!(created instanceof PredictiveSurvivalConfigScreen screen)) {
                    throw new AssertionError("Mod Menu factory did not create PredictiveSurvivalConfigScreen: " + created);
                }
                minecraft.setScreen(screen);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError("could not invoke Predictive Survival Mod Menu integration", exception);
            }
        });

        context.waitFor(minecraft -> minecraft.screen instanceof PredictiveSurvivalConfigScreen);
        context.runOnClient(minecraft -> minecraft.screen.onClose());
        context.waitFor(minecraft -> !(minecraft.screen instanceof PredictiveSurvivalConfigScreen));
    }
}
