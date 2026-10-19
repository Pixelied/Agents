package dev.pixelied.maceswap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;

public final class PredictiveMaceSwapClient implements ClientModInitializer {
    private static SwapConfig config;
    private static SwapController controller;

    public static SwapConfig config() { return config; }
    public static void applyConfig(SwapConfig next) {
        next.normalize();
        config = next;
        config.save();
        if (controller != null) controller.setConfig(config);
    }
    public static void recordAttackAttempt() { if (controller != null) controller.recordAttackAttempt(); }
    public static String status() { return controller == null ? "Not in a world" : controller.status(); }

    @Override
    public void onInitializeClient() {
        config = SwapConfig.load();
        controller = new SwapController(Minecraft.getInstance(), config);
        ClientTickEvents.END_CLIENT_TICK.register(client -> controller.tick());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> controller.onDisconnect());
    }
}
