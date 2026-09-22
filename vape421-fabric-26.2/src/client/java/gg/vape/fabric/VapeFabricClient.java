package gg.vape.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VapeFabricClient implements ClientModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("vape421");

    @Override
    public void onInitializeClient() {
        var loader = FabricLoader.getInstance();
        var mcVersion = loader.getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
        LOGGER.info("Vape 4.21 Fabric bootstrap on Minecraft {}, Java {}, OS {}/{}",
                mcVersion, Runtime.version(), System.getProperty("os.name"), System.getProperty("os.arch"));
        LOGGER.info("Legacy EXE/DLL/JVMTI injection bootstrap is disabled.");
    }
}
