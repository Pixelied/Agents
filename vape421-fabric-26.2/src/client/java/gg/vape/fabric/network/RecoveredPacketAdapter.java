package gg.vape.fabric.network;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;

public final class RecoveredPacketAdapter {
    public record Result(boolean cancelled, Object packet) { }
    private record EventFactory(Constructor<?> constructor, Method fire, Method packetInstance) { }
    private static final Map<String, EventFactory> CACHE = new ConcurrentHashMap<>();
    private static volatile Logger logger;
    private static volatile boolean enabled;
    private RecoveredPacketAdapter() { }
    public static void enable(Logger eventLogger) { logger = eventLogger; enabled = true; }
    public static Result outbound(Object connection, Object packet) { return fire("gg.vape.event.impl.EventPacketSend", connection, packet); }
    public static Result inbound(Object connection, Object packet) { return fire("gg.vape.event.impl.EventPacketReceive", connection, packet); }
    private static Result fire(String name, Object connection, Object packet) {
        if (!enabled) return new Result(false, packet);
        try {
            EventFactory f=CACHE.computeIfAbsent(name, RecoveredPacketAdapter::load);
            Object e=f.constructor().newInstance(connection, packet);
            boolean cancelled=Boolean.TRUE.equals(f.fire().invoke(e));
            Object replacement=f.packetInstance().invoke(e);
            return new Result(cancelled, replacement == null ? packet : replacement);
        } catch(Throwable failure) {
            if(logger!=null) logger.error("Recovered packet event dispatch failed for {}", name, failure);
            return new Result(false, packet);
        }
    }
    private static EventFactory load(String name) {
        try {
            Class<?> t=Class.forName(name,true,RecoveredPacketAdapter.class.getClassLoader());
            return new EventFactory(t.getConstructor(Object.class,Object.class),t.getMethod("fire"),t.getMethod("getPacketInstance"));
        } catch(ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
