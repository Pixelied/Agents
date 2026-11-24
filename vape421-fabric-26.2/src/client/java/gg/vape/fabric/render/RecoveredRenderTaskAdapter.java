package gg.vape.fabric.render;

import java.lang.reflect.Method;
import org.slf4j.Logger;

public final class RecoveredRenderTaskAdapter {
    private static volatile boolean enabled;
    private static volatile Method runPendingTasks;
    private static volatile Logger logger;

    private RecoveredRenderTaskAdapter() { }

    public static void enable(Logger eventLogger) {
        logger = eventLogger;
        enabled = true;
    }

    public static void drain() {
        if (!enabled) return;

        try {
            Method method = runPendingTasks;
            if (method == null) {
                method = resolve();
                runPendingTasks = method;
            }
            method.invoke(null);
        } catch (Throwable failure) {
            enabled = false;
            Logger current = logger;
            if (current != null) {
                current.error("Recovered render-thread task queue failed; disabling queue bridge", failure);
            }
        }
    }

    private static synchronized Method resolve() throws ReflectiveOperationException {
        if (runPendingTasks != null) return runPendingTasks;
        Class<?> type = Class.forName(
                "gg.vape.utils.RenderThreadTaskQueue",
                true,
                RecoveredRenderTaskAdapter.class.getClassLoader());
        return type.getMethod("runPendingTasks");
    }
}
