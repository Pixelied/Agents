package gg.vape.runtime;

/**
 * Stable entrypoint used by the Fabric shell. The recovered native bridge's
 * start method now contains Java-only initialization logic, so Fabric can call
 * it without loading a DLL or attaching JVMTI.
 */
public final class VapeBootstrap {
    private static boolean started;

    private VapeBootstrap() {
    }

    public static synchronized void start() throws Throwable {
        if (started) {
            return;
        }
        NativeBridge.start();
        started = true;
    }
}
