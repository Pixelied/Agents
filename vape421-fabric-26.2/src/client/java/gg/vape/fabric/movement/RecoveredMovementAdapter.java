package gg.vape.fabric.movement;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public final class RecoveredMovementAdapter {
    public record PreResult(boolean cancelled, Vec3 movement) { }

    private static volatile boolean enabled;
    private static volatile Logger logger;
    private static volatile Constructor<?> preConstructor;
    private static volatile Method preFire;
    private static volatile Method preGetVector;
    private static volatile Constructor<?> postConstructor;
    private static volatile Method postFire;

    private RecoveredMovementAdapter() { }

    public static void enable(Logger eventLogger) {
        logger = eventLogger;
        enabled = true;
    }

    public static PreResult pre(Vec3 movement) {
        if (!enabled) return new PreResult(false, movement);
        try {
            ensureResolved();
            Object event = preConstructor.newInstance(movement);
            boolean cancelled = Boolean.TRUE.equals(preFire.invoke(event));
            Object replacement = preGetVector.invoke(event);
            return new PreResult(cancelled, replacement instanceof Vec3 vec ? vec : movement);
        } catch (Throwable failure) {
            Logger current = logger;
            if (current != null) current.error("Recovered pre-move event dispatch failed", failure);
            return new PreResult(false, movement);
        }
    }

    public static void post(Vec3 movement) {
        if (!enabled) return;
        try {
            ensureResolved();
            Object event = postConstructor.newInstance(movement);
            postFire.invoke(event);
        } catch (Throwable failure) {
            Logger current = logger;
            if (current != null) current.error("Recovered post-move event dispatch failed", failure);
        }
    }

    private static synchronized void ensureResolved() throws ReflectiveOperationException {
        if (preConstructor != null) return;
        ClassLoader loader = RecoveredMovementAdapter.class.getClassLoader();
        Class<?> preType = Class.forName("gg.vape.event.impl.EventPreMove", true, loader);
        Class<?> postType = Class.forName("gg.vape.event.impl.EventPostMove", true, loader);
        preConstructor = preType.getConstructor(Object.class);
        preFire = preType.getMethod("fire");
        preGetVector = preType.getMethod("getVector");
        postConstructor = postType.getConstructor(Object.class);
        postFire = postType.getMethod("fire");
    }
}
