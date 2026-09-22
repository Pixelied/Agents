package gg.vape.runtime;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class PlatformServices {
    public interface Provider {
        default String keyName(long keyCode) { return Long.toString(keyCode); }
        default boolean isKeyDown(int keyCode) { return false; }
        default int mapVirtualKey(int value, int mode) { return value; }
        default String accessToken() { return ""; }
        default void copyToClipboard(String text) { }
        default void openUri(String uri) { }
        default Path dataPath(String relativePath) {
            return Paths.get(System.getProperty("user.home", "."), ".vape421")
                    .resolve(relativePath).toAbsolutePath().normalize();
        }
        default void sendMouse(int mode, int value) { }
        default void traceState(int state) { }
        default void log(String message) { System.err.println("[Vape421] " + message); }
        default byte[] classBytes(Class<?> targetClass) { return new byte[0]; }
        default byte[] resourceBytes(String name) { return new byte[0]; }
        default int replaceClassBytes(Class<?> targetClass, byte[] bytecode) { return 0; }
        default String loadSettings(String key) { return null; }
        default void saveSettings(String value) { }
    }

    private static final AtomicReference<Provider> PROVIDER =
            new AtomicReference<>(new Provider() { });

    private PlatformServices() { }
    public static Provider get() { return PROVIDER.get(); }
    public static void install(Provider provider) { PROVIDER.set(Objects.requireNonNull(provider)); }

    public static Object invoke(Method method, Object target, Object... arguments) {
        try {
            method.setAccessible(true);
            return method.invoke(target, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to invoke " + method, e);
        }
    }
}
