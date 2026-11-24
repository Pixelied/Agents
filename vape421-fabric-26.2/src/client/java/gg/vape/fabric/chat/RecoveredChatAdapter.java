package gg.vape.fabric.chat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

public final class RecoveredChatAdapter {
    private record Factory(
            Constructor<?> constructor,
            Method fire,
            Method outputContent) { }

    private static volatile boolean enabled;
    private static volatile Logger logger;
    private static volatile Factory factory;

    private RecoveredChatAdapter() { }

    public static void enable(Logger eventLogger) {
        logger = eventLogger;
        enabled = true;
    }

    public static Component rewrite(
            Object chatComponent,
            Component content,
            Object messageSignature,
            Object guiMessageTag) {
        if (!enabled) return content;

        try {
            Factory current = factory;
            if (current == null) {
                current = resolve();
                factory = current;
            }

            Object event = current.constructor().newInstance(
                    chatComponent, content, messageSignature, guiMessageTag);
            current.fire().invoke(event);
            Object rewritten = current.outputContent().invoke(event);
            return rewritten instanceof Component component ? component : content;
        } catch (Throwable failure) {
            Logger currentLogger = logger;
            if (currentLogger != null) {
                currentLogger.error("Recovered chat-message render event failed", failure);
            }
            return content;
        }
    }

    private static synchronized Factory resolve() throws ReflectiveOperationException {
        if (factory != null) return factory;

        Class<?> type = Class.forName(
                "gg.vape.event.impl.EventChatMessageRender",
                true,
                RecoveredChatAdapter.class.getClassLoader());
        return new Factory(
                type.getConstructor(Object.class, Object.class, Object.class, Object.class),
                type.getMethod("fire"),
                type.getMethod("getOutputContentComponent"));
    }
}
