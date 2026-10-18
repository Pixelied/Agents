package gg.vape.fabric.platform;

import com.mojang.blaze3d.platform.InputConstants;
import gg.vape.fabric.input.FabricInputBridge;
import gg.vape.runtime.FileBackedPlatformServices;
import java.awt.Desktop;
import java.net.URI;
import java.nio.file.Path;
import net.minecraft.client.KeyMapping;
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

    private long window() { return Minecraft.getInstance().getWindow().handle(); }

    @Override
    public String accessToken() {
        String token = System.getProperty("vape.accessToken");
        if (token == null || token.isBlank()) token = System.getenv("VAPE_ACCESS_TOKEN");
        return token == null ? "" : token.trim();
    }

    @Override
    public boolean isKeyDown(int legacyVirtualKey) {
        int mouse = LegacyVirtualKeyMap.mouseButton(legacyVirtualKey);
        if (mouse >= 0) return GLFW.glfwGetMouseButton(window(), mouse) == GLFW.GLFW_PRESS;
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
    public void sendMouse(int mode, int message) {
        int button = switch (message) {
            case 513, 514 -> GLFW.GLFW_MOUSE_BUTTON_LEFT;
            case 516, 517 -> GLFW.GLFW_MOUSE_BUTTON_RIGHT;
            case 519, 520 -> GLFW.GLFW_MOUSE_BUTTON_MIDDLE;
            default -> -1;
        };
        if (button < 0) return;
        boolean pressed = message == 513 || message == 516 || message == 519;
        dispatchMouseButton(button, pressed);
    }

    @Override
    public boolean postLegacyInput(int message, long firstArgument, long secondArgument) {
        switch (message) {
            case 256, 260 -> { dispatchKeyboardKey((int) firstArgument, true); return true; }
            case 257, 261 -> { dispatchKeyboardKey((int) firstArgument, false); return true; }
            case 513, 514, 516, 517, 519, 520 -> { sendMouse(0, message); return true; }
            case 523, 524 -> {
                int xButton = (int)((firstArgument >>> 16) & 0xffffL);
                int glfwButton = xButton == 1 ? GLFW.GLFW_MOUSE_BUTTON_4
                        : xButton == 2 ? GLFW.GLFW_MOUSE_BUTTON_5 : -1;
                if (glfwButton >= 0) {
                    dispatchMouseButton(glfwButton, message == 523);
                    return true;
                }
            }
            default -> { return false; }
        }
        return false;
    }

    private void dispatchKeyboardKey(int legacyVirtualKey, boolean pressed) {
        int glfwKey = LegacyVirtualKeyMap.key(legacyVirtualKey);
        if (glfwKey == GLFW.GLFW_KEY_UNKNOWN) return;
        int scancode = GLFW.glfwGetKeyScancode(glfwKey);
        FabricInputBridge.onKey(glfwKey, scancode, pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
        InputConstants.Key key = InputConstants.Type.KEYSYM.getOrCreate(glfwKey);
        KeyMapping.set(key, pressed);
        if (pressed) KeyMapping.click(key);
    }

    private void dispatchMouseButton(int glfwButton, boolean pressed) {
        FabricInputBridge.onMouseButton(glfwButton, pressed ? GLFW.GLFW_PRESS : GLFW.GLFW_RELEASE, 0);
        InputConstants.Key key = InputConstants.Type.MOUSE.getOrCreate(glfwButton);
        KeyMapping.set(key, pressed);
        if (pressed) KeyMapping.click(key);
    }

    @Override
    public void copyToClipboard(String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text == null ? "" : text);
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
    public void log(String message) { logger.info("{}", message); }
}
