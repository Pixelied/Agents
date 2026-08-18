package dev.pixelied.survival;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.config.SurvivalConfigStore;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.debug.SurvivalDebugHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.nio.file.Path;

public final class PredictiveSurvivalClient implements ClientModInitializer {
    private SurvivalEngine engine;
    private MinecraftSurvivalRuntime runtime;

    @Override
    public void onInitializeClient() {
        Minecraft minecraft = Minecraft.getInstance();
        SurvivalConfig config = loadConfig();
        runtime = new MinecraftSurvivalRuntime(minecraft);
        engine = new SurvivalEngine(
            config,
            runtime,
            new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
        );

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player != null && client.level != null) {
                engine.tick();
            }
        });

        HudRenderCallback.EVENT.register((graphics, tickCounter) -> {
            if (!engine.config().debugEnabled()) return;
            runtime.lastFrame().ifPresent(frame -> {
                var lines = SurvivalDebugHud.lines(
                    engine.config(),
                    frame,
                    engine.currentPlan(),
                    engine.executionStatus()
                );
                int y = 6;
                for (String line : lines) {
                    graphics.drawString(minecraft.font, line, 6, y, 0xFFFFFF, true);
                    y += 10;
                }
            });
        });
    }

    private static SurvivalConfig loadConfig() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("predictive_survival.json");
        SurvivalConfigStore store = new SurvivalConfigStore(path);
        try {
            SurvivalConfig config = store.load();
            store.save(config);
            return config;
        } catch (IOException ignored) {
            return SurvivalConfig.defaults();
        }
    }
}
