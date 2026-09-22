package gg.vape.fabric;

import gg.vape.fabric.platform.FabricPlatformServices;
import gg.vape.fabric.render.FabricFontAtlasServices;
import gg.vape.fabric.render.FabricRenderBridge;
import gg.vape.fabric.render.FabricRenderServices;
import gg.vape.runtime.FontAtlasServices;
import gg.vape.runtime.PlatformServices;
import gg.vape.runtime.RenderServices;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VapeFabricClient implements ClientModInitializer {
    private static final String MOD_ID = "vape421";
    private static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        var loader = FabricLoader.getInstance();
        var configDir = loader.getConfigDir().resolve(MOD_ID).toAbsolutePath().normalize();
        PlatformServices.install(new FabricPlatformServices(configDir, LOGGER));

        var renderServices = new FabricRenderServices();
        RenderServices.install(renderServices);
        FontAtlasServices.install(new FabricFontAtlasServices(renderServices));
        FabricRenderBridge.installRenderServices(renderServices);

        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(MOD_ID, "hud"),
                FabricRenderBridge::extractHud);
        LevelExtractionEvents.END_EXTRACTION.register(FabricRenderBridge::extractLevel);
        LevelRenderEvents.END_MAIN.register(FabricRenderBridge::renderLevel);

        ClientLifecycleEvents.CLIENT_STARTED.register(
                client -> RecoveredCoreLauncher.startIfPresent(client, LOGGER));

        var mcVersion = loader.getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LOGGER.info("Vape 4.21 Fabric bootstrap on Minecraft {}, Java {}, OS {}/{}",
                mcVersion, Runtime.version(), System.getProperty("os.name"), System.getProperty("os.arch"));
        LOGGER.info("Legacy EXE/DLL/JVMTI injection bootstrap is disabled.");
    }
}
