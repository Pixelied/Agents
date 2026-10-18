package gg.vape.runtime;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Stable entrypoint used by the Fabric shell.
 *
 * <p>The migration shell deliberately does not compile the recovered core yet,
 * so this class must not have a static bytecode reference to NativeBridge.
 * Once the verified recovered core is present, start() resolves and invokes
 * the Java-only NativeBridge bootstrap through the same class loader.
 */
public final class VapeBootstrap {
    private static boolean started;

    private VapeBootstrap() {
    }

    public static synchronized void start() throws Throwable {
        if (started) {
            return;
        }

        try {
            Class<?> bridge = Class.forName(
                    "gg.vape.runtime.NativeBridge",
                    true,
                    VapeBootstrap.class.getClassLoader());
            Method start = bridge.getMethod("start");
            start.invoke(null);
            started = true;
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            throw cause == null ? failure : cause;
        }
    }
}
