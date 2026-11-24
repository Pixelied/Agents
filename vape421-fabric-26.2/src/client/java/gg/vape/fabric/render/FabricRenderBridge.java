package gg.vape.fabric.render;

import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class FabricRenderBridge {
    public interface Listener {
        default void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) { }
        default void extractLevel(LevelExtractionContext context) { }
        default void renderLevel(LevelRenderContext context) { }
    }

    private static final CopyOnWriteArrayList<Listener> LISTENERS = new CopyOnWriteArrayList<>();
    private static volatile FabricRenderServices renderServices;
    private static volatile FabricProjectionServices projectionServices;

    private FabricRenderBridge() { }

    public static void installRenderServices(FabricRenderServices services) {
        renderServices = services;
    }

    public static void installProjectionServices(FabricProjectionServices services) {
        projectionServices = services;
    }

    public static void addListener(Listener listener) {
        if (listener != null) LISTENERS.addIfAbsent(listener);
    }

    public static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    public static void extractHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        FabricRenderServices services = renderServices;
        if (services == null) return;

        RecoveredRenderTaskAdapter.drain();

        try (FabricRenderServices.Scope ignored = services.begin(graphics)) {
            for (Listener listener : LISTENERS) {
                listener.extractHud(graphics, deltaTracker);
            }
        }
    }

    public static void extractLevel(LevelExtractionContext context) {
        FabricProjectionServices projections = projectionServices;
        if (projections != null) {
            projections.capture(context);
        }

        RecoveredRenderTaskAdapter.drain();
        for (Listener listener : LISTENERS) {
            listener.extractLevel(context);
        }
    }

    public static void renderLevel(LevelRenderContext context) {
        for (Listener listener : LISTENERS) {
            listener.renderLevel(context);
        }
    }
}
