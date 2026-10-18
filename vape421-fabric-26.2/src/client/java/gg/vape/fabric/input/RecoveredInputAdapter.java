package gg.vape.fabric.input;

import gg.vape.fabric.platform.LegacyVirtualKeyMap;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

public final class RecoveredInputAdapter implements FabricInputBridge.Listener {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private final Constructor<?> keyEventConstructor;
    private final Constructor<?> mouseEventConstructor;
    private final Method keyEventFire;
    private final Method mouseEventFire;
    private final Logger logger;

    private RecoveredInputAdapter(Logger logger) throws ReflectiveOperationException {
        ClassLoader loader = RecoveredInputAdapter.class.getClassLoader();
        Class<?> keyEvent = Class.forName("gg.vape.event.impl.EventKeyPress", true, loader);
        Class<?> mouseEvent = Class.forName("gg.vape.event.impl.EventMouseButton", true, loader);
        this.keyEventConstructor = keyEvent.getConstructor(int.class, boolean.class);
        this.mouseEventConstructor = mouseEvent.getConstructor(int.class, boolean.class);
        this.keyEventFire = keyEvent.getMethod("fire");
        this.mouseEventFire = mouseEvent.getMethod("fire");
        this.logger = logger;
    }

    public static void install(Logger logger) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            FabricInputBridge.addListener(new RecoveredInputAdapter(logger));
            logger.info("Recovered Vape input events attached to Minecraft 26.2 input callbacks.");
        } catch (ReflectiveOperationException missingCorePiece) {
            INSTALLED.set(false);
            logger.debug("Recovered input events are not available in this migration build yet.");
        }
    }

    @Override
    public boolean onKey(int key, int scancode, int action, int modifiers) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE && action != GLFW.GLFW_REPEAT) return false;
        int legacyVirtualKey = LegacyVirtualKeyMap.virtualKey(key);
        if (legacyVirtualKey == 0) return false;
        return fire(keyEventConstructor, keyEventFire, legacyVirtualKey, action != GLFW.GLFW_RELEASE);
    }

    @Override
    public boolean onMouseButton(int button, int action, int modifiers) {
        if (action != GLFW.GLFW_PRESS && action != GLFW.GLFW_RELEASE) return false;
        return fire(mouseEventConstructor, mouseEventFire, button, action == GLFW.GLFW_PRESS);
    }

    private boolean fire(Constructor<?> constructor, Method fireMethod, int code, boolean down) {
        try {
            Object event = constructor.newInstance(code, down);
            Object result = fireMethod.invoke(event);
            return result instanceof Boolean consumed && consumed;
        } catch (ReflectiveOperationException eventFailure) {
            logger.error("Recovered Vape input event dispatch failed", eventFailure);
            return false;
        }
    }
}
