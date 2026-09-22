package gg.vape.fabric.platform;

import gg.vape.runtime.FileBackedPlatformServices;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

public final class FabricPlatformServices extends FileBackedPlatformServices {
    private final Path dataDirectory;
    private final Logger logger;

    public FabricPlatformServices(Path configDirectory, Logger logger) {
        super(configDirectory);
        this.dataDirectory = configDirectory;
        this.logger = logger;
    }

    private long window() {
        return Minecraft.getInstance().getWindow().getWindow();
    }

    @Override
    public boolean isKeyDown(int legacyVirtualKey) {
        int mouse = LegacyVirtualKeyMap.mouseButton(legacyVirtualKey);
        if (mouse >= 0) {
            return GLFW.glfwGetMouseButton(window(), mouse) == GLFW.GLFW_PRESS;
        }
        int key = LegacyVirtualKeyMap.key(legacyVirtualKey);
        return key != GLFW.GLFW_KEY_UNKNOWN && GLFW.glfwGetKey(window(), key) == GLFW.GLFW_PRESS;
    }

    @Override
    public String keyName(long keyMetadata) {
        int scanCode = (int)((keyMetadata >>> 16) & 0xffffL);
        String name = GLFW.glfwGetKeyName(GLFW.GLFW_KEY_UNKNOWN, scanCode);
        return name == null ? "Unknown" : name;
    }

    @Override
    public void copyToClipboard(String text) {
        GLFW.glfwSetClipboardString(window(), text == null ? "" : text);
    }

    @Override
    public void openUri(String uri) {
        try {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI.create(uri));
        } catch (Exception e) {
            log("Unable to open URI " + uri + ": " + e.getMessage());
        }
    }

    @Override
    public Path dataPath(String relativePath) {
        return dataDirectory.resolve(relativePath).toAbsolutePath().normalize();
    }

    @Override
    public void log(String message) {
        logger.info("{}", message);
    }
}
