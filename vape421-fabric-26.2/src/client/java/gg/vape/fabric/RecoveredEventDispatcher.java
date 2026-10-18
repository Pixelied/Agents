package gg.vape.fabric;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public final class RecoveredEventDispatcher {
    private record EventFactory(Constructor<?> constructor, Method fireMethod) { }

    private static final Map<String, EventFactory> CACHE = new ConcurrentHashMap<>();
    private static volatile Logger logger;
    private static volatile boolean coreRunning;

    private RecoveredEventDispatcher() { }

    public static void markCoreRunning(Logger eventLogger) {
        logger = eventLogger;
        coreRunning = true;
    }

    public static boolean fire(String className, Class<?>[] parameterTypes, Object... arguments) {
        if (!coreRunning) return false;
        try {
            String cacheKey = className + '#' + Arrays.toString(parameterTypes);
            EventFactory factory = CACHE.computeIfAbsent(
                    cacheKey, ignored -> create(className, parameterTypes));
            Object event = factory.constructor().newInstance(arguments);
            Object result = factory.fireMethod().invoke(event);
            return result instanceof Boolean canceled && canceled;
        } catch (Throwable failure) {
            Logger currentLogger = logger;
            if (currentLogger != null) {
                currentLogger.error("Recovered event dispatch failed for {}", className, failure);
            }
            return false;
        }
    }

    private static EventFactory create(String className, Class<?>[] parameterTypes) {
        try {
            Class<?> type = Class.forName(
                    className, true, RecoveredEventDispatcher.class.getClassLoader());
            return new EventFactory(
                    type.getConstructor(parameterTypes),
                    type.getMethod("fire"));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "Recovered event class is unavailable: " + className, e);
        }
    }
}
