package dev.adrien.spearclient;

import com.mojang.blaze3d.platform.InputConstants;
import dev.adrien.spearclient.combat.AttackSequencer;
import dev.adrien.spearclient.combat.SpearController;
import dev.adrien.spearclient.config.ConfigStore;
import dev.adrien.spearclient.config.SpearConfig;
import dev.adrien.spearclient.modules.InfiniteReachModule;
import dev.adrien.spearclient.modules.LungeBoostModule;
import dev.adrien.spearclient.modules.OneTapModule;
import dev.adrien.spearclient.network.PacketSender;
import dev.adrien.spearclient.network.ServerStateTracker;
import dev.adrien.spearclient.ui.SpearConfigScreen;
import java.io.IOException;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpearClient implements ClientModInitializer {
    public static final String MOD_ID = "spearclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SpearClient instance;

    private SpearConfig config = SpearConfig.defaults();
    private ConfigStore configStore;
    private KeyMapping openConfigKey;
    private final ServerStateTracker tracker = ServerStateTracker.shared();
    private final PacketSender packets = new PacketSender(tracker);
    private final AttackSequencer sequencer = new AttackSequencer(packets, tracker);
    private final OneTapModule oneTap = new OneTapModule(true);
    private final LungeBoostModule lungeBoost = new LungeBoostModule(true);
    private final InfiniteReachModule infiniteReach = new InfiniteReachModule(true);
    private final SpearController controller = new SpearController(
        () -> config,
        sequencer,
        oneTap,
        lungeBoost,
        infiniteReach
    );

    @Override
    public void onInitializeClient() {
        instance = this;
        Path configPath = FabricLoader.getInstance().getConfigDir().resolve("spearclient.json");
        configStore = new ConfigStore(configPath);
        config = configStore.load();

        KeyMapping.Category category = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(MOD_ID, "main")
        );
        openConfigKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.spearclient.open_config",
            InputConstants.Type.KEYSYM,
            InputConstants.KEY_O,
            category
        ));

        ClientTickEvents.END_LEVEL_TICK.register(level -> controller.tick(Minecraft.getInstance()));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openConfigKey.consumeClick()) {
                client.setScreen(new SpearConfigScreen(client.screen, configStore(), config));
            }
        });
        LOGGER.info("Spear Client initialized for Minecraft 26.1.2");
    }

    public static SpearClient instance() {
        if (instance == null) {
            throw new IllegalStateException("Spear Client is not initialized");
        }
        return instance;
    }

    public SpearController controller() {
        return controller;
    }

    public SpearConfig config() {
        return config;
    }

    public ConfigStore configStore() {
        if (configStore == null) {
            throw new IllegalStateException("Spear Client config store is not initialized");
        }
        return configStore;
    }

    public void setConfig(SpearConfig config) {
        this.config = config == null ? SpearConfig.defaults() : config.sanitized();
    }

    public boolean saveConfig(SpearConfig config) {
        SpearConfig sanitized = config == null ? SpearConfig.defaults() : config.sanitized();
        try {
            configStore().save(sanitized);
            this.config = sanitized;
            return true;
        } catch (IOException failure) {
            LOGGER.error("Failed to save Spear Client config", failure);
            return false;
        }
    }
}
