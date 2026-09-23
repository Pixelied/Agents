package gg.vape.fabric;

import java.util.concurrent.atomic.AtomicBoolean;

import gg.vape.Vape;
import gg.vape.event.impl.EventRender2D;
import gg.vape.event.impl.EventRender3D;
import gg.vape.event.impl.EventRenderTracers3D;
import gg.vape.fabric.render.FabricRenderBackend;
import gg.vape.runtime.FabricCompatibility;
import gg.vape.runtime.NativeBridge;
import gg.vape.ui.click.GuiScreenNativeCallbackBridge;
import gg.vape.utils.render.OpenGlBackendHolder;
import gg.vape.wrapper.impl.MatrixStack;
import gg.vape.wrapper.impl.RenderManager;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Migration gate between Fabric's lifecycle and the recovered client core.
 * Fabric owns bootstrap/hook installation; the historical injector/JVMTI path
 * is never invoked.
 */
public final class FabricMigrationRuntime {
    private static final Logger LOGGER = LoggerFactory.getLogger("Vape421/FabricRuntime");
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static final AtomicBoolean CORE_STARTED = new AtomicBoolean();
    private static volatile Minecraft minecraft;
    private static volatile FabricRenderBackend renderBackend;
    private static volatile float worldPartialTicks;

    private FabricMigrationRuntime() {
    }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) {
            return;
        }

        FabricRenderBackend backend = new FabricRenderBackend();
        renderBackend = backend;
        OpenGlBackendHolder.forceBufferedBackend();
        FabricCompatibility.install(backend);
        FabricHookRegistry.register();
    }

    static void onMinecraftStarted(Minecraft client) {
        minecraft = client;
        try {
            NativeBridge.wh(client.getWindow().handle());
            NativeBridge.start();
            boolean ready = Vape.INSTANCE != null && Vape.INSTANCE.getModManager() != null;
            CORE_STARTED.set(ready);
            RecoveredEventBridge.refreshCoreAvailability();
            if (ready) {
                LOGGER.info("Recovered Vape 4.21 core started under Fabric 26.2");
            } else {
                LOGGER.error("Recovered Vape 4.21 core bootstrap returned without an initialized ModManager");
            }
        } catch (Throwable error) {
            CORE_STARTED.set(false);
            LOGGER.error("Recovered Vape 4.21 core failed to start", error);
        }
    }

    static void onMinecraftStopping(Minecraft client) {
        CORE_STARTED.set(false);
        try {
            if (Vape.INSTANCE != null) {
                Vape.INSTANCE.saveAndStop();
            }
        } catch (Throwable error) {
            LOGGER.warn("Failed to flush recovered client state during shutdown", error);
        }
        FabricCompatibility.clear();
        renderBackend = null;
        minecraft = null;
    }

    static void onScreenInitialized(Screen screen) {
        // Observation-only. GuiMixin fires EventGuiOpen at the exact setScreen
        // entry point so ScreenEvents must not duplicate it.
    }

    static void onLevelExtraction(DeltaTracker deltaTracker) {
        worldPartialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
    }

    static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (!isCoreStarted()) {
            return;
        }
        FabricRenderBackend backend = renderBackend;
        if (backend == null) {
            return;
        }

        float partialTicks = deltaTracker.getGameTimeDeltaPartialTick(false);
        backend.beginGui(graphics);
        try {
            // Keep the recovered rendering contract. EventRender2D renders the
            // HUD and fires module listeners; the explicit screen callback is
            // the normal ClickGUI path previously tied to PostRenderTick.
            EventRender2D.create();
            GuiScreenNativeCallbackBridge.drawScreen(null, 0, 0, partialTicks);
            backend.flushGuiBatches(partialTicks, false);
        } catch (Throwable error) {
            LOGGER.error("Recovered HUD extraction failed", error);
        } finally {
            backend.endGui();
        }
    }

    static void renderWorld(LevelRenderContext context) {
        if (!isCoreStarted()) {
            return;
        }
        FabricRenderBackend backend = renderBackend;
        if (backend == null) {
            return;
        }

        float partialTicks = worldPartialTicks;
        backend.beginWorld(context);
        try {
            RenderManager.updateInterpolatedRenderPosition(partialTicks);
            MatrixStack stack = new MatrixStack(context.poseStack());
            if (EventRender3D.getEventListeners().hasListeners()) {
                new EventRender3D(stack, partialTicks).fire();
            }
            if (EventRenderTracers3D.getEventListeners().hasListeners()) {
                new EventRenderTracers3D(stack, partialTicks).fire();
            }
            backend.flushWorldBatches(partialTicks);
        } catch (Throwable error) {
            LOGGER.error("Recovered world render submission failed", error);
        } finally {
            backend.endWorld();
        }
    }

    public static Minecraft minecraft() {
        return minecraft;
    }

    public static boolean isInitialized() {
        return INITIALIZED.get();
    }

    public static boolean isCoreStarted() {
        return CORE_STARTED.get();
    }

    public static FabricRenderBackend renderBackend() {
        return renderBackend;
    }
}
