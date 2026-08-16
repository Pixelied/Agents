package dev.adrien.spearclient;

import dev.adrien.spearclient.combat.AttackSequencer;
import dev.adrien.spearclient.combat.SpearController;
import dev.adrien.spearclient.config.SpearConfig;
import dev.adrien.spearclient.modules.InfiniteReachModule;
import dev.adrien.spearclient.modules.OneTapModule;
import dev.adrien.spearclient.network.PacketSender;
import dev.adrien.spearclient.network.ServerStateTracker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SpearClient implements ClientModInitializer {
    public static final String MOD_ID = "spearclient";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static SpearClient instance;

    private SpearConfig config = SpearConfig.defaults();
    private final ServerStateTracker tracker = ServerStateTracker.shared();
    private final PacketSender packets = new PacketSender(tracker);
    private final AttackSequencer sequencer = new AttackSequencer(packets, tracker);
    private final OneTapModule oneTap = new OneTapModule(true);
    private final InfiniteReachModule infiniteReach = new InfiniteReachModule(true);
    private final SpearController controller = new SpearController(
        () -> config,
        sequencer,
        oneTap,
        infiniteReach
    );

    @Override
    public void onInitializeClient() {
        instance = this;
        ClientTickEvents.END_LEVEL_TICK.register(level -> controller.tick(Minecraft.getInstance()));
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

    public void setConfig(SpearConfig config) {
        this.config = config == null ? SpearConfig.defaults() : config.sanitized();
    }
}
