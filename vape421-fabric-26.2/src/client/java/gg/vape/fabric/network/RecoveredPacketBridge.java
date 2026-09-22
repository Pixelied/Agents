package gg.vape.fabric.network;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import org.slf4j.Logger;

/**
 * Reflection boundary to the recovered packet events. Keeping this adapter
 * independent lets the Fabric shell compile while the recovered core is ported
 * into the build incrementally.
 */
public final class RecoveredPacketBridge {
    public record Result(Object packet, boolean canceled, boolean modified) {
        public static Result pass(Object packet) {
            return new Result(packet, false, false);
        }
    }

    private record EventAccess(Constructor<?> constructor, Method fire, Method getPacketInstance) { }

    private static volatile EventAccess sendAccess;
    private static volatile EventAccess receiveAccess;
    private static volatile Logger logger;
    private static volatile boolean coreRunning;
    private static final ThreadLocal<Integer> BYPASS_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    private RecoveredPacketBridge() { }

    public static void markCoreRunning(Logger eventLogger) {
        logger = eventLogger;
        coreRunning = true;
    }

    public static boolean isBypassing() {
        return BYPASS_DEPTH.get() > 0;
    }

    public static void runBypassing(Runnable action) {
        int previous = BYPASS_DEPTH.get();
        BYPASS_DEPTH.set(previous + 1);
        try {
            action.run();
        } finally {
            if (previous == 0) BYPASS_DEPTH.remove();
            else BYPASS_DEPTH.set(previous);
        }
    }

    public static Result outgoing(Object connection, Object packet) {
        return dispatch(true, connection, packet);
    }

    public static Result incoming(Object connection, Object packet) {
        return dispatch(false, connection, packet);
    }

    private static Result dispatch(boolean outgoing, Object connection, Object packet) {
        if (!coreRunning || isBypassing() || packet == null) return Result.pass(packet);
        try {
            EventAccess access = outgoing ? sendAccess : receiveAccess;
            if (access == null) {
                access = create(outgoing
                        ? "gg.vape.event.impl.EventPacketSend"
                        : "gg.vape.event.impl.EventPacketReceive");
                if (outgoing) sendAccess = access;
                else receiveAccess = access;
            }
            Object event = access.constructor().newInstance(connection, packet);
            Object fireResult = access.fire().invoke(event);
            boolean canceled = fireResult instanceof Boolean value && value;
            Object replacement = access.getPacketInstance().invoke(event);
            if (replacement == null) replacement = packet;
            return new Result(replacement, canceled, replacement != packet);
        } catch (Throwable failure) {
            Logger currentLogger = logger;
            if (currentLogger != null) {
                currentLogger.error("Recovered {} packet dispatch failed",
                        outgoing ? "outgoing" : "incoming", failure);
            }
            return Result.pass(packet);
        }
    }

    private static EventAccess create(String className) throws ReflectiveOperationException {
        Class<?> type = Class.forName(
                className, true, RecoveredPacketBridge.class.getClassLoader());
        return new EventAccess(
                type.getConstructor(Object.class, Object.class),
                type.getMethod("fire"),
                type.getMethod("getPacketInstance"));
    }
}
