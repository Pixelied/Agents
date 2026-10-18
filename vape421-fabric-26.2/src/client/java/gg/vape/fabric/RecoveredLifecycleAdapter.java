package gg.vape.fabric;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;

public final class RecoveredLifecycleAdapter {
    private static boolean installed;

    private RecoveredLifecycleAdapter() { }

    public static synchronized void install(Logger logger) {
        if (installed) return;
        RecoveredEventDispatcher.markCoreRunning(logger);
        ClientTickEvents.START_CLIENT_TICK.register(client ->
                RecoveredEventDispatcher.fire("gg.vape.event.impl.EventPreTick", new Class<?>[0]));
        ClientTickEvents.END_CLIENT_TICK.register(client ->
                RecoveredEventDispatcher.fire("gg.vape.event.impl.EventPostTick", new Class<?>[0]));
        installed = true;
        logger.info("Recovered Vape tick events attached to Fabric lifecycle callbacks.");
    }
}
