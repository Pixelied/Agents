package gg.vape.fabric.playerlist;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public final class RecoveredTabNameAdapter {
    private static volatile Factory factory;
    private static volatile Logger logger;

    private record Factory(Constructor<?> constructor, Method fire, Method getDisplayNameInstance) { }

    private RecoveredTabNameAdapter() { }

    public static void enable(Logger eventLogger) {
        logger = eventLogger;
    }

    public static Component rewrite(Object overlay, Object playerInfo, Component vanillaName) {
        Logger currentLogger = logger;
        if (currentLogger == null) return vanillaName;

        try {
            Factory current = factory;
            if (current == null) {
                current = load();
                factory = current;
            }

            Object event = current.constructor().newInstance(overlay, playerInfo);
            boolean changed = Boolean.TRUE.equals(current.fire().invoke(event));
            if (!changed) return vanillaName;

            Object replacement = current.getDisplayNameInstance().invoke(event);
            return replacement instanceof Component component ? component : vanillaName;
        } catch (Throwable failure) {
            currentLogger.error("Recovered tab-list display-name event failed", failure);
            return vanillaName;
        }
    }

    private static Factory load() {
        try {
            Class<?> type = Class.forName(
                    "gg.vape.event.impl.EventPlayerTabOverlayDisplayName",
                    true,
                    RecoveredTabNameAdapter.class.getClassLoader());
            return new Factory(
                    type.getConstructor(Object.class, Object.class),
                    type.getMethod("fire"),
                    type.getMethod("getDisplayNameInstance"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Recovered tab-list display-name event is unavailable", e);
        }
    }
}
