package gg.vape.fabric;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

/** A small, platform-specific JNI library loaded by the JVM, not an injector. */
public final class InProcessBytecode {
    private static boolean loaded;
    private InProcessBytecode() {}
    public static synchronized void load() throws IOException {
        if (loaded) return;
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        String cpu = (arch.equals("aarch64") || arch.equals("arm64")) ? "arm64" :
                (arch.equals("x86_64") || arch.equals("amd64")) ? "x64" : arch;
        String platform = os.contains("mac") || os.contains("darwin") ? "macos" :
                os.contains("win") ? "windows" : os.contains("linux") ? "linux" : os;
        String file = platform.equals("windows") ? "vape_bridge.dll" :
                platform.equals("macos") ? "libvape_bridge.dylib" : "libvape_bridge.so";
        String resource = "/fabric-native/" + platform + "-" + cpu + "/" + file;
        try (InputStream in = InProcessBytecode.class.getResourceAsStream(resource)) {
            if (in == null) throw new IOException("No bridge for " + platform + "-" + cpu + ": " + resource);
            Path directory = Files.createTempDirectory("vape421-bridge-");
            Path target = directory.resolve(file);
            directory.toFile().deleteOnExit();
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            target.toFile().deleteOnExit();
            System.load(target.toAbsolutePath().toString());
            loaded = true;
        }
    }
    private static native byte[] capture0(Class<?> type);
    private static native int redefine0(Class<?> type, byte[] data);
    public static synchronized byte[] capture(Class<?> type) {
        if (!loaded) throw new IllegalStateException("Class bridge not loaded");
        return capture0(type);
    }
    public static synchronized int redefine(Class<?> type, byte[] data) {
        if (!loaded) throw new IllegalStateException("Class bridge not loaded");
        return redefine0(type, data);
    }
}
