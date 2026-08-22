package dev.pixelied.survival;

import dev.pixelied.survival.config.SurvivalConfig;
import dev.pixelied.survival.config.SurvivalConfigStore;
import dev.pixelied.survival.config.LiveConfigController;
import dev.pixelied.survival.config.PredictiveSurvivalConfigScreen;
import dev.pixelied.survival.core.EngineLimits;
import dev.pixelied.survival.core.MinecraftSurvivalRuntime;
import dev.pixelied.survival.core.SurvivalEngine;
import dev.pixelied.survival.debug.DecisionHistory;
import dev.pixelied.survival.debug.SurvivalDebugHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.util.Objects;
import java.util.function.Predicate;

public final class PredictiveSurvivalClient implements ClientModInitializer {
    private static final Identifier DEBUG_HUD_ID = Identifier.fromNamespaceAndPath("predictive_survival", "debug");
    private static final String CLIENT_GAMETEST_MOD_ID = "predictive_survival_gametest";

    private static PredictiveSurvivalClient instance;

    private SurvivalEngine engine;
    private MinecraftSurvivalRuntime runtime;
    private SurvivalConfigStore configStore;
    private LiveConfigController liveConfigController;

    @Override
    public void onInitializeClient() {
        instance = this;
        FabricLoader loader = FabricLoader.getInstance();
        if (!shouldStartAutomation(loader::isModLoaded)) return;

        Minecraft minecraft = Minecraft.getInstance();
        configStore = new SurvivalConfigStore(loader.getConfigDir().resolve("predictive_survival.json"));
        SurvivalConfig config = loadConfig(configStore);
        runtime = new MinecraftSurvivalRuntime(minecraft);
        engine = new SurvivalEngine(
            config,
            runtime,
            new DecisionHistory(EngineLimits.defaults().maxDecisionHistory())
        );
        liveConfigController = new LiveConfigController(configStore, engine::replaceConfig);

        ClientTickEvents.START_CLIENT_TICK.register(client -> {
            if (client.player != null && client.level != null) {
                engine.tick();
            }
        });

        HudElementRegistry.attachElementBefore(
            VanillaHudElements.CHAT,
            DEBUG_HUD_ID,
            this::extractDebugHud
        );
    }

    static boolean shouldStartAutomation(Predicate<String> isModLoaded) {
        return !Objects.requireNonNull(isModLoaded, "isModLoaded").test(CLIENT_GAMETEST_MOD_ID);
    }

    private void extractDebugHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!engine.config().debugEnabled()) return;
        Minecraft minecraft = Minecraft.getInstance();
        runtime.lastFrame().ifPresent(frame -> {
            var lines = SurvivalDebugHud.lines(
                engine.config(),
                frame,
                engine.currentPlan(),
                engine.executionStatus()
            );
            int y = 6;
            for (String line : lines) {
                graphics.text(minecraft.font, line, 6, y, 0xFFFFFFFF, true);
                y += 10;
            }
        });
    }

    public static Screen createConfigScreen(Screen parent) {
        PredictiveSurvivalClient current = instance;
        if (current == null || current.engine == null || current.liveConfigController == null) {
            return parent;
        }
        return new PredictiveSurvivalConfigScreen(parent, current.engine.config(), current.liveConfigController);
    }

    private static SurvivalConfig loadConfig(SurvivalConfigStore store) {
        try {
            SurvivalConfig config = store.load();
            store.save(config);
            return config;
        } catch (IOException ignored) {
            return SurvivalConfig.defaults();
        }
    }
}
