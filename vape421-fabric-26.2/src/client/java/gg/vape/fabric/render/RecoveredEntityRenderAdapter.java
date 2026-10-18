package gg.vape.fabric.render;

import java.lang.reflect.Method;
import org.slf4j.Logger;

public final class RecoveredEntityRenderAdapter {
    private static volatile boolean enabled;
    private static volatile Logger logger;
    private static volatile Method preRenderCall;

    private RecoveredEntityRenderAdapter() { }

    public static void enable(Logger eventLogger) {
        logger = eventLogger;
        enabled = true;
    }

    public static void preRender(Object entity) {
        if (!enabled || entity == null) return;

        try {
            Method method = preRenderCall;
            if (method == null) {
                method = resolve();
                preRenderCall = method;
            }
            method.invoke(null, entity);
        } catch (Throwable failure) {
            enabled = false;
            Logger current = logger;
            if (current != null) {
                current.error("Recovered pre-render entity callback failed; disabling bridge", failure);
            }
        }
    }

    private static synchronized Method resolve() throws ReflectiveOperationException {
        if (preRenderCall != null) return preRenderCall;
        Class<?> type = Class.forName(
                "gg.vape.mapping.EventPreRenderEntityCallback",
                true,
                RecoveredEntityRenderAdapter.class.getClassLoader());
        return type.getMethod("call", Object.class);
    }
}
