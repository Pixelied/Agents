package dev.adrien.spearclient;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpearClient implements ClientModInitializer {
    public static final String MOD_ID = "spearclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Spear Client initialized for Minecraft 26.1.2");
    }
}
