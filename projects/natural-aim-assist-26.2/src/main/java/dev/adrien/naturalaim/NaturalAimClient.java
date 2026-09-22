package dev.adrien.naturalaim;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NaturalAimClient implements ClientModInitializer {
    public static final String MOD_ID = "naturalaim";
    public static final Logger LOGGER = LoggerFactory.getLogger("Natural Aim Assist");

    private static NaturalAimConfig config;
    private static NaturalAimEngine engine;

    @Override
    public void onInitializeClient() {
        config = NaturalAimConfig.load();
        engine = new NaturalAimEngine(config);
        LOGGER.info("Natural Aim Assist initialized for Minecraft 26.2");
    }

    public static NaturalAimConfig config() {
        if (config == null) {
            config = NaturalAimConfig.load();
        }
        return config;
    }

    public static NaturalAimEngine engine() {
        if (engine == null) {
            engine = new NaturalAimEngine(config());
        }
        return engine;
    }
}
