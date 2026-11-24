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
        default boolean postLegacyInput(int message, long firstArgument, long secondArgument) { return false; }
        default void traceState(int state) { }

        // Optional loader/online integrations from the former external
        // controller. Fabric owns the client lifecycle, so these defaults
        // deliberately remain local/no-op unless a real provider is installed.
        default void reportLoadCompleted() {
            log("Vape 4.21 core initialization completed.");
        }
        default void setUsername(String username) { }
        default void updatePresence(String serverDescription, String clientDescription) { }

        // Legacy native font calls are retired. The Fabric port uses the
        // FontAtlasServices and RenderServices backends instead.
        default int drawString(int fontId, String text, double x, double y,
                               int color, float scale) { return 0; }
        default int makeFont(int fontId, int style, String text) { return 0; }

        // No remote loader settings service is available in the Fabric client.
        default boolean[] onlineSettingsState() { return new boolean[]{false, false}; }

        default void log(String message) { System.err.println("[Vape421] " + message); }
        default byte[] classBytes(Class<?> targetClass) { return new byte[0]; }
        default byte[] resourceBytes(String name) { return new byte[0]; }

        /**
         * Fabric does not support the legacy JVMTI class-redefinition contract.
         * Never return zero unless a provider actually replaced the loaded class.
         */
        default int replaceClassBytes(Class<?> targetClass, byte[] bytecode) { return -95; }

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
