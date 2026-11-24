package gg.vape.fabric.render;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.slf4j.Logger;

public final class RecoveredTabNameAdapter {
    private static volatile boolean enabled;
    private static volatile Logger logger;
    private static volatile Constructor<?> constructor;
    private static volatile Method fire;
    private static volatile Method getDisplayNameInstance;

    private RecoveredTabNameAdapter() { }

    public static void enable(Logger eventLogger) {
        logger = eventLogger;
        enabled = true;
    }

    public static Object replacement(Object overlay, Object playerInfo) {
        if (!enabled) return null;
        try {
            ensureResolved();
            Object event = constructor.newInstance(overlay, playerInfo);
            if (!Boolean.TRUE.equals(fire.invoke(event))) return null;
            return getDisplayNameInstance.invoke(event);
        } catch (Throwable failure) {
            Logger current = logger;
            if (current != null) current.error("Recovered tab-name event dispatch failed", failure);
            return null;
        }
    }

    private static synchronized void ensureResolved() throws ReflectiveOperationException {
        if (constructor != null) return;
        Class<?> type = Class.forName(
                "gg.vape.event.impl.EventPlayerTabOverlayDisplayName",
                true, RecoveredTabNameAdapter.class.getClassLoader());
        constructor = type.getConstructor(Object.class, Object.class);
        fire = type.getMethod("fire");
        getDisplayNameInstance = type.getMethod("getDisplayNameInstance");
    }
}
